Extract news items from this newsletter email. For each distinct news story or article mentioned:

1. title: A concise title for the news item
2. summary: 2-3 sentence summary of the key points
3. url: The original article URL if present in the email (null if not found)

Return up to 5 items, prioritizing the most prominent/relevant stories. If the email contains no distinct news items, return a single item summarizing the email content.

Email Subject: {subject}
Email Content: {content}
