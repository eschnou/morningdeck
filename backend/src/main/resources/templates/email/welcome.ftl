<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Welcome to Morning Deck</title>
    <style type="text/css">
        /* Notion-style warm monochrome palette */
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7f6f3; margin: 0; padding: 24px; }
        .container { max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 40px; box-shadow: 0 1px 3px rgba(55, 53, 47, 0.08); }
        h1 { color: #1c1a19; font-size: 28px; font-weight: 600; margin: 0 0 24px 0; line-height: 1.3; letter-spacing: -0.02em; }
        .body-text { font-size: 15px; line-height: 1.65; color: #37352f; margin-bottom: 24px; }
        .button { background: #2a2725; border-radius: 6px; color: #ffffff !important; display: inline-block; font-size: 14px; font-weight: 500; padding: 12px 24px; text-align: center; text-decoration: none; }
        .button:hover { background: #37352f; }
        .divider { height: 1px; background: #e9e7e4; margin: 28px 0; }
        .footer { font-size: 13px; line-height: 1.6; color: #9b9a97; }
        .footer a { color: #37352f; text-decoration: underline; }
    </style>
</head>
<body>
<div class="container">
    <h1>Welcome to Morning Deck</h1>

    <div class="body-text">
        Hello ${fullName},
        <br><br>
        Thank you for signing up for Morning Deck! We're excited to have you on board.
        <br><br>
        Morning Deck helps you stay informed with personalized news briefings delivered right to your inbox. Set up your first briefing to get started.
    </div>

    <div style="text-align: center; margin: 32px 0;">
        <a href="https://${domain}/briefs" class="button">Get Started</a>
    </div>

    <div class="divider"></div>

    <div class="footer">
        If you have any questions, please contact us at
        <a href="mailto:support@transcode.be">support@transcode.be</a>
    </div>
</div>
</body>
</html>
