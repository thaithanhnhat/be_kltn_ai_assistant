# AI Chatbot Integration Guide

This guide explains how the AI-powered chatbot system works in our application. The system integrates with Telegram and Facebook bots to provide natural language interactions with shop customers.

## Overview

The AI chatbot system uses Gemini AI to process customer messages and generate intelligent, context-aware responses. It follows these key steps:

1. A customer message is received through Telegram or Facebook
2. The message and shop context are sent to Gemini AI
3. The AI generates a structured response with actions
4. The system processes the response and takes appropriate actions
5. The human-readable part of the response is sent back to the customer

## Features

The AI chatbot can handle:

- Product inquiries and recommendations
- Order placement and tracking
- Customer support questions
- Processing delivery information
- Order cancellations and modifications

## Technical Components

### ShopAIService

The core component that handles AI integration. It has these main methods:

- `processCustomerMessage`: Process general customer chat messages
- `getProductRecommendations`: Get product suggestions based on customer queries
- `processOrderRequest`: Handle order-related requests
- `validateDeliveryInfo`: Check and validate customer delivery information

### Response Format

The AI returns structured JSON responses with these fields:

```json
{
  "response_text": "Human-readable response to show to the customer",
  "detected_intent": "PRODUCT_INQUIRY | PLACE_ORDER | CANCEL_ORDER | DELIVERY_STATUS | GENERAL_QUERY",
  "action_required": true/false,
  "action_details": {
    "action_type": "CREATE_ORDER | CHECK_STATUS | CANCEL_ORDER",
    "params": {
      "param1": "value1",
      "param2": "value2"
    }
  },
  "missing_information": ["name", "phone", "address"]
}
```

### Integration with Messaging Platforms

#### Telegram

The system integrates with Telegram through:

1. `ShopTelegramBot`: Handles message receiving and sending
2. `TelegramBotManager`: Manages bot instances for different shops

When a Telegram message is received:
- The message is stored in the database
- The message is sent to the AI service for processing
- The AI response is parsed and sent back to the user
- Any required actions are logged and can be processed

#### Facebook Messenger

The system integrates with Facebook through:

1. `FacebookBotService`: Interface for bot operations
2. `FacebookBotServiceImpl`: Implementation handling message receiving and sending

When a Facebook message is received:
- The webhook endpoint receives the message
- The message is processed by the AI service
- The AI response is parsed and sent back to the user
- Any required actions are logged and can be processed

## Configuration

The system uses the following configuration in `application.yaml`:

```yaml
app:
  gemini:
    api-key: ${GEMINI_API_KEY}
    chat-model-id: gemini-2.5-flash-preview-04-17
    model-id: gemini-2.0-flash-exp-image-generation
    api-url: https://generativelanguage.googleapis.com/v1beta/models
```

## Intents and Actions

The system detects the following customer intents:

1. **PRODUCT_INQUIRY**: Customer asking about product availability, details, or recommendations
2. **PLACE_ORDER**: Customer wants to place an order
3. **CANCEL_ORDER**: Customer wants to cancel an existing order
4. **DELIVERY_STATUS**: Customer inquiring about order status or delivery time
5. **GENERAL_QUERY**: General questions or conversations

## Missing Information Handling

When a customer attempts an action that requires specific information (like placing an order), the AI will identify and request any missing details:

```json
{
  "response_text": "I'd be happy to place that order for you. Could you please provide your delivery address?",
  "detected_intent": "PLACE_ORDER",
  "action_required": false,
  "missing_information": ["address"]
}
```

## Example Interaction Flow

1. **Customer**: "Do you have Coca-Cola in stock?"
2. **System**:
   - Sends message + shop product data to AI
   - AI returns structured response
   - System sends response to customer
3. **AI Response**:
   ```json
   {
     "response_text": "Yes, we have Coca-Cola in stock! We have both regular and diet options, priced at $1.50 per can or $5 for a six-pack. Would you like to place an order?",
     "detected_intent": "PRODUCT_INQUIRY",
     "action_required": false,
     "action_details": {},
     "missing_information": []
   }
   ```
4. **Customer**: "Yes, I'd like to order a six-pack of regular Coca-Cola"
5. **System**:
   - Sends message + context to AI
   - AI identifies order intent but needs delivery info
6. **AI Response**:
   ```json
   {
     "response_text": "Great! I'll help you order a six-pack of regular Coca-Cola. To process your order, I'll need your delivery information. Could you please provide your full name, phone number, and delivery address?",
     "detected_intent": "PLACE_ORDER",
     "action_required": false,
     "action_details": {
       "product_ids": [12],
       "quantities": [1]
     },
     "missing_information": ["name", "phone", "address"]
   }
   ```

## Best Practices

1. **Context Preservation**: The system provides shop and product context to the AI
2. **Error Handling**: Both technical errors and incomplete/invalid customer requests are handled gracefully
3. **Structured Responses**: All AI responses follow a consistent JSON structure
4. **Security**: API keys and tokens are configured through environment variables or secure storage
5. **Logging**: All interactions are logged for monitoring and debugging purposes 