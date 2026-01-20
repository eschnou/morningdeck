Extract news items from this web page based on the user's extraction criteria.

For each item found, provide:
1. title: A concise title for the news item
2. content: 2-3 sentence summary/description
3. link: The URL to the item (can be relative or absolute)

Return up to 10 items. If no items match the criteria, return an empty array.

User's extraction criteria: {extractionPrompt}

Page content:
{pageContent}
