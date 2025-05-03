# Facebook Messenger Bot Integration Guide

## Overview

This guide explains how to integrate your shop with Facebook Messenger using our API. This integration enables automated conversations between your shop and your customers on Facebook Messenger.

## Setup Process

### 1. Configure Webhook

First, you need to configure a webhook for your shop:

```http
POST /api/facebook/shops/{shopId}/configure
```

**Path Parameters:**
- `shopId`: Your shop ID

**Response:**
```json
{
  "webhookUrl": "https://your-server.com/assistant/api/facebook/webhook/123",
  "verifyToken": "uniqueRandomToken"
}
```

### 2. Set Up Facebook App

1. Go to [Facebook Developer Portal](https://developers.facebook.com/)
2. Create a new app or use an existing one
3. Add Messenger product to your app
4. Under Messenger Settings, go to "Webhooks" section
5. Click "Add Callback URL"
6. Enter the `webhookUrl` and `verifyToken` from the previous step
7. Subscribe to events: `messages`, `messaging_postbacks`

### 3. Save Page Access Token

After successfully setting up the webhook, get a Page Access Token from Facebook Developer Portal and save it:

```http
POST /api/facebook/shops/{shopId}/access-token?accessToken={pageAccessToken}
```

**Path Parameters:**
- `shopId`: Your shop ID

**Query Parameters:**
- `accessToken`: Page Access Token obtained from Facebook

### 4. Start the Bot

Once the access token is saved, you can start the bot:

```http
POST /api/facebook/shops/{shopId}/start
```

**Path Parameters:**
- `shopId`: Your shop ID

### 5. Check Bot Status

To check if your bot is running:

```http
GET /api/facebook/shops/{shopId}/status
```

**Path Parameters:**
- `shopId`: Your shop ID

**Response:**
```json
{
  "shopId": 123,
  "active": true,
  "webhookUrl": "https://your-server.com/assistant/api/facebook/webhook/123"
}
```

### 6. Stop the Bot

To stop the bot:

```http
POST /api/facebook/shops/{shopId}/stop
```

**Path Parameters:**
- `shopId`: Your shop ID

## Testing the Integration

1. After starting the bot, send a message to your Facebook Page through Messenger
2. The bot should respond automatically
3. Check server logs for any issues

## Troubleshooting

1. **Webhook Verification Failed**: Ensure the verify token matches exactly what you configured
2. **No Messages Being Received**: Check if you've subscribed to the correct events on Facebook
3. **Authentication Errors**: Verify your access token is correct and hasn't expired

## Security Considerations

- Keep your access token secure
- Implement proper authentication for your API endpoints
- Consider implementing rate limiting for your webhook endpoints

## Best Practices

1. Use a persistent database to store conversation history
2. Implement graceful error handling in your message processing logic
3. Consider message queue for handling high message volumes
4. Set up monitoring for your bot's uptime and performance 