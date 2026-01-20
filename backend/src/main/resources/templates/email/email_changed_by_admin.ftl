<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Email Address Changed - Morning Deck</title>
    <style type="text/css">
        /* Notion-style warm monochrome palette */
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7f6f3; margin: 0; padding: 24px; }
        .container { max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 40px; box-shadow: 0 1px 3px rgba(55, 53, 47, 0.08); }
        h1 { color: #1c1a19; font-size: 24px; font-weight: 600; margin: 0 0 24px 0; line-height: 1.3; letter-spacing: -0.01em; }
        .body-text { font-size: 15px; line-height: 1.65; color: #37352f; margin-bottom: 24px; }
        .info-box { background: #f7f6f3; border: 1px solid #e9e7e4; border-radius: 6px; padding: 14px 16px; margin: 24px 0; font-size: 14px; color: #37352f; }
        .info-box strong { font-weight: 600; }
        .footer { font-size: 13px; line-height: 1.6; color: #9b9a97; }
        .footer a { color: #37352f; text-decoration: underline; }
    </style>
</head>
<body>
<div class="container">
    <h1>Email Address Changed</h1>

    <div class="body-text">
        Hello ${fullName},
        <br><br>
        Your email address has been changed by an administrator.
    </div>

    <div class="info-box">
        New email address: <strong>${newEmail}</strong>
    </div>

    <div class="footer">
        If you did not request this change, please contact support immediately at
        <a href="mailto:support@transcode.be">support@transcode.be</a>
    </div>
</div>
</body>
</html>
