<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>暂不支持预览</title>
    <style type="text/css">
        * {
            box-sizing: border-box;
        }

        html, body {
            height: 100%;
            margin: 0;
        }

        body {
            display: flex;
            align-items: center;
            justify-content: center;
            background-color: #f7f8fa;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
        }

        .empty-state {
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
            padding: 32px 24px;
            max-width: 520px;
        }

        .icon-doc {
            width: 56px;
            height: 56px;
            color: #595959;
            margin-bottom: 28px;
            flex-shrink: 0;
        }

        .title {
            margin: 0 0 14px;
            font-size: 18px;
            font-weight: 600;
            line-height: 1.4;
            color: #262626;
        }

        .detail {
            margin: 0;
            max-width: 480px;
            font-size: 14px;
            line-height: 1.6;
            font-weight: 400;
            color: #8c8c8c;
            word-break: break-word;
        }

        .detail:empty {
            display: none;
        }
    </style>
</head>
<body>
<div class="empty-state">
    <svg class="icon-doc" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <path d="M14 10h24l14 14v30a4 4 0 0 1-4 4H14a4 4 0 0 1-4-4V14a4 4 0 0 1 4-4z" stroke="currentColor" stroke-width="2.25" stroke-linejoin="round"/>
        <path d="M38 10v14h14" stroke="currentColor" stroke-width="2.25" stroke-linejoin="round"/>
        <path d="M22 36h20M22 44h20M22 28h12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    </svg>
    <h1 class="title">该文件暂不支持在线预览</h1>
    <#if msg?? && msg?has_content>
        <p class="detail">${msg}</p>
    </#if>
</div>
</body>
</html>
