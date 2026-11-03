import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const fixtureBase = process.env.FIXTURE_BASE_URL || 'http://127.0.0.1:18080';
const fixturePath = new URL('../fixtures/sample-inline-html.md', import.meta.url);
const normalizeLines = (text: string) => text.replace(/\r\n/g, '\n');

test('preserves Markdown through Java download/cache, Base64 transport, and browser preview', async ({ page }) => {
  const source = await readFile(fixturePath, 'utf8');
  const sourceUrl = `${fixtureBase}/sample-inline-html.md`;
  const encodedUrl = encodeURIComponent(Buffer.from(sourceUrl).toString('base64'));
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => { (window as any).__markdownExecution = 0; });

  // Exercise both backend branches with the same source URL. No page or asset routing mocks.
  for (const refresh of [true, false]) {
    const response = await page.goto(`/onlinePreview?url=${encodedUrl}&forceUpdatedCache=${refresh}`);
    expect(response?.ok()).toBeTruthy();
    const transported = Buffer.from(await page.locator('#textData').inputValue(), 'base64').toString('utf8');
    expect(normalizeLines(transported)).toBe(normalizeLines(source));
    await expect(page.locator('#markdown h1')).toHaveText('新浪集团反舞弊举报范围');
    await expect(page.locator('#markdown li').first()).toHaveText('员工侵占、挪用等行为。');
    await expect(page.locator('#markdown span').filter({ hasText: '举报材料' }))
      .toHaveCSS('color', 'rgb(255, 0, 0)');
    await expect(page.locator('#markdown')).not.toContainText('<span');
    await expect(page.locator('#markdown')).toContainText('<literal-tag> & 原文。');
    await expect(page.locator('#markdown script, #markdown literal-tag')).toHaveCount(0);

    await page.getByRole('button', { name: '源代码', exact: true }).click();
    expect(normalizeLines(await page.locator('#markdown pre').textContent() || '')).toBe(normalizeLines(source));
    await page.getByRole('button', { name: '预览模式', exact: true }).click();
    await expect(page.locator('#markdown')).not.toContainText('<span');
    expect(await page.evaluate(() => (window as any).__markdownExecution)).toBe(0);
  }
  expect(errors).toEqual([]);
});
