import { test, expect, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const resourceRoot = fileURLToPath(new URL('../../../server/src/main/resources/', import.meta.url));
const staticRoot = path.join(resourceRoot, 'static');
const previewOrigin = 'http://markdown-preview.test';

function jsStringContent(value: string): string {
  return JSON.stringify(value).slice(1, -1).replace(/'/g, "\\'")
    .replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');
}

async function openMarkdown(page: Page, markdown: string, keyword = '') {
  // Render only the server-provided values; exercise the real template and assets.
  const template = await readFile(path.join(resourceRoot, 'web/markdown.ftl'), 'utf8');
  const html = template
    .replace(/<#include\s+[^>]+>/g, '')
    .replaceAll('${file.name}', 'markdown-regression.md')
    .replaceAll('${textData}', Buffer.from(markdown).toString('base64'))
    .replaceAll('${highlightall?js_string}', jsStringContent(keyword))
    .replaceAll('${highlightall}', keyword)
    .replaceAll("${'$'}", '$');

  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => { (window as any).__markdownExecution = 0; });
  // No Java server, fixture server, CDN, or external network is needed.
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== previewOrigin) {
      await route.abort();
      return;
    }
    if (url.pathname === '/') {
      await route.fulfill({ contentType: 'text/html; charset=utf-8', body: html });
      return;
    }
    const assetPath = path.resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    if (!assetPath.startsWith(staticRoot + path.sep)) {
      await route.fulfill({ status: 404, body: '' });
      return;
    }
    try {
      await route.fulfill({ path: assetPath });
    } catch {
      await route.fulfill({ status: 404, body: '' });
    }
  });
  await page.goto(previewOrigin + '/');
  await expect(page.locator('.CodeMirror')).toHaveCount(1);
  await expect(page.locator('#markdown')).not.toHaveText('');
  expect(errors).toEqual([]);
  return errors;
}

async function setEditorContent(page: Page, content: string) {
  await page.evaluate(value => (window as any).editor.setValue(value), content);
}

async function expectNoExecution(page: Page) {
  // Let image error handlers and other queued browser callbacks run if present.
  const execution = await page.evaluate(async () => {
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    return (window as any).__markdownExecution;
  });
  expect(execution).toBe(0);
}

test('renders Markdown with inline HTML and preserves the original source across view changes', async ({ page }) => {
  const markdown = [
    '# 新浪集团反舞弊举报范围',
    '',
    '**举报重点**',
    '',
    '<span style="color: ;">空颜色不应变成标签源码</span>',
    '',
    '<span style="color: red">有效颜色</span>',
    '',
    '- 第一项',
    '- 第二项 & 原文',
  ].join('\n');
  const errors = await openMarkdown(page, markdown);

  await expect(page.locator('#markdown h1')).toHaveText('新浪集团反舞弊举报范围');
  await expect(page.locator('#markdown strong')).toHaveText('举报重点');
  await expect(page.locator('#markdown li')).toHaveCount(2);
  await expect(page.locator('#markdown span').filter({ hasText: '空颜色不应变成标签源码' })).toHaveCount(1);
  await expect(page.locator('#markdown span').filter({ hasText: '有效颜色' })).toHaveCSS('color', 'rgb(255, 0, 0)');
  await expect(page.locator('#markdown')).not.toContainText('<span');
  expect(await page.evaluate(() => (window as any).editor.getValue())).toBe(markdown);

  await page.getByRole('button', { name: '源代码', exact: true }).click();
  expect(await page.locator('#markdown pre').textContent()).toBe(markdown);
  await expect(page.locator('#markdown pre span')).toHaveCount(0);
  await page.getByRole('button', { name: '预览模式', exact: true }).click();
  await expect(page.locator('#markdown h1')).toHaveText('新浪集团反舞弊举报范围');
  await expect(page.locator('#markdown')).not.toContainText('<span');
  expect(errors).toEqual([]);
});

const hostileMarkdown = [
  '# Sanitized document',
  '',
  '<script>window.__markdownExecution=1</script>',
  '<img src="missing-image" onerror="window.__markdownExecution=2">',
  '<a href="javascript:window.__markdownExecution=3" onclick="window.__markdownExecution=4">unsafe link</a>',
  '<iframe srcdoc="<script>parent.__markdownExecution=5</script>"></iframe>',
  '<svg onload="window.__markdownExecution=6"><a href="javascript:window.__markdownExecution=7">SVG</a></svg>',
  '<span onmouseover="window.__markdownExecution=8" style="color:red; position:fixed; background-image:url(javascript:alert(1)); width:expression(alert(1)); behavior:url(x); -moz-binding:url(x)">safe styled text</span>',
].join('\n');

for (const entry of ['initial load', 'editor update']) {
  test(`sanitizes HTML, attributes, links, and CSS on ${entry}`, async ({ page }) => {
    const errors = await openMarkdown(page, entry === 'initial load' ? hostileMarkdown : '# Initial document');
    if (entry === 'editor update') {
      await setEditorContent(page, hostileMarkdown);
    }
    await expect(page.locator('#markdown h1')).toHaveText('Sanitized document');
    await expect(page.locator('#markdown script, #markdown iframe, #markdown svg, #markdown object, #markdown embed')).toHaveCount(0);
    await expect(page.locator('#markdown [onerror], #markdown [onclick], #markdown [onload], #markdown [onmouseover], #markdown [srcdoc]')).toHaveCount(0);
    const hrefs = await page.locator('#markdown [href]').evaluateAll(elements => elements.map(element => element.getAttribute('href')));
    expect(hrefs.every(href => !/^(?:javascript|vbscript|data):/i.test((href || '').trim()))).toBeTruthy();
    const styles = await page.locator('#markdown [style]').evaluateAll(elements => elements.map(element => element.getAttribute('style') || ''));
    expect(styles.join('\n')).not.toMatch(/url\s*\(|expression\s*\(|javascript\s*:|position\s*:|behavior\s*:|binding\s*:/i);
    await expect(page.locator('#markdown span').filter({ hasText: 'safe styled text' })).toHaveCSS('color', 'rgb(255, 0, 0)');
    await expectNoExecution(page);

    await page.getByRole('button', { name: '源代码', exact: true }).click();
    expect(await page.locator('#markdown pre').textContent()).toBe(hostileMarkdown);
    await expect(page.locator('#markdown pre script, #markdown pre img, #markdown pre iframe')).toHaveCount(0);
    await page.getByRole('button', { name: '预览模式', exact: true }).click();
    await expect(page.locator('#markdown h1')).toHaveText('Sanitized document');
    await expectNoExecution(page);
    expect(errors).toEqual([]);
  });
}

test('highlights visible text without changing HTML attributes, links, or code', async ({ page }) => {
  const markdown = [
    '# Policy overview',
    '',
    '<span title="Policy attribute" style="color:red">Policy text</span>',
    '',
    '[Policy link](https://example.test/Policy?q=Policy "Policy title")',
    '',
    '`Policy inline`',
    '',
    '```html',
    '<span title="Policy">Policy code</span>',
    '```',
  ].join('\n');
  await openMarkdown(page, markdown, 'Policy');
  await expect(page.locator('#markdown .highlight-keyword')).toHaveCount(3);
  await expect(page.locator('#markdown span[title="Policy attribute"]')).toHaveText('Policy text');
  const link = page.locator('#markdown a');
  await expect(link).toHaveAttribute('href', 'https://example.test/Policy?q=Policy');
  await expect(link).toHaveAttribute('title', 'Policy title');
  await expect(link).toHaveText('Policy link');
  await expect(page.locator('#markdown code .highlight-keyword, #markdown pre .highlight-keyword')).toHaveCount(0);
  await expect(page.locator('#markdown p code')).toHaveText('Policy inline');
  await expect(page.locator('#markdown pre code')).toHaveClass('language-html');
  expect(await page.locator('#markdown pre code').textContent()).toBe('<span title="Policy">Policy code</span>\n');
  await page.getByRole('button', { name: '源代码', exact: true }).click();
  expect(await page.locator('#markdown pre').textContent()).toBe(markdown);
  await page.getByRole('button', { name: '预览模式', exact: true }).click();
  await expect(page.locator('#markdown .highlight-keyword')).toHaveCount(3);
});

test('renders entity-encoded heading text safely in the table of contents', async ({ page }) => {
  const heading = '<img src=x onerror="window.__markdownExecution=1">';
  await openMarkdown(page, '# &lt;img src=x onerror=&quot;window.__markdownExecution=1&quot;&gt;');
  await expect(page.locator('#markdown h1')).toHaveText(heading);
  await expect(page.locator('#directory a')).toHaveText(heading);
  await expect(page.locator('#directory a')).toHaveAttribute('title', heading);
  await expect(page.locator('#directory img, #directory script, #directory [onerror]')).toHaveCount(0);
  await expectNoExecution(page);
});

test('treats a quote-containing highlight keyword as data', async ({ page }) => {
  const keyword = "');window.__markdownExecution=1;//";
  const errors = await openMarkdown(page, '# Search keyword\n\n' + keyword, keyword);
  await expect(page.locator('#markdown .highlight-keyword')).toHaveText(keyword);
  await expectNoExecution(page);
  expect(errors).toEqual([]);
});

test('removes document classes that could cover the viewer controls', async ({ page }) => {
  const markdown = '# Visible document\n\n<div class="modal-backdrop in">Document overlay text</div>';
  const errors = await openMarkdown(page, markdown);
  const overlay = page.locator('#markdown div').filter({ hasText: 'Document overlay text' });
  await expect(overlay).toHaveText('Document overlay text');
  expect(await overlay.getAttribute('class')).toBeNull();

  const sourceButton = page.getByRole('button', { name: '源代码', exact: true });
  await expect(sourceButton).toBeVisible();
  const buttonReceivesPointer = await sourceButton.evaluate(button => {
    const bounds = button.getBoundingClientRect();
    const target = document.elementFromPoint(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    return target === button || (target !== null && button.contains(target));
  });
  expect(buttonReceivesPointer).toBeTruthy();
  await sourceButton.click();
  expect(await page.locator('#markdown pre').textContent()).toBe(markdown);
  await expect(page.locator('#markdown .modal-backdrop')).toHaveCount(0);

  await page.getByRole('button', { name: '预览模式', exact: true }).click();
  await expect(page.locator('#markdown h1')).toHaveText('Visible document');
  await expect(page.locator('#markdown .modal-backdrop')).toHaveCount(0);
  await expect(sourceButton).toBeVisible();
  expect(errors).toEqual([]);
});
