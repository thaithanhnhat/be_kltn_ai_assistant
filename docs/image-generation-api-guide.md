# Image Generation API with Gemini AI

This guide explains how to use the AI image generation API that allows shop owners to modify product images using AI through text prompts.

## API Endpoint

**POST** `/api/products/image-generation`

## Prerequisites

Before using this API, ensure that:

1. You have a valid user account and authentication token
2. You have permission to modify the product
3. The product already has an image in base64 format

## Request Format

```json
{
  "productId": 123,
  "prompt": "Change the background to a beach scene",
  "fileName": "beach_product.png",
  "modelId": "gemini-2.0-flash-exp-image-generation"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `productId` | Long | Yes | ID of the product whose image should be modified |
| `prompt` | String | Yes | Text description of how to modify the image |
| `fileName` | String | No | Optional name for the generated image file |
| `modelId` | String | No | Optional Gemini model ID to use (defaults to configured model) |

## Response Format

```json
{
  "productId": 123,
  "originalImageUrl": "/api/products/123/image",
  "generatedImageUrl": "/api/products/123/image?t=1687245896325",
  "prompt": "Change the background to a beach scene",
  "generatedAt": "2023-06-20T14:31:36.325Z",
  "status": "SUCCESS",
  "message": "Image generated successfully"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `productId` | Long | ID of the modified product |
| `originalImageUrl` | String | URL to access the original product image |
| `generatedImageUrl` | String | URL to access the newly generated image |
| `prompt` | String | The text prompt that was used |
| `generatedAt` | DateTime | When the image was generated |
| `status` | String | Status of the operation (`SUCCESS` or `ERROR`) |
| `message` | String | Success message or error details |

## Example Usage

### Request

```bash
curl -X POST \
  http://localhost:8080/assistant/api/products/image-generation \
  -H 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": 123,
    "prompt": "Change the model to be wearing sunglasses but keep the same clothing"
  }'
```

### Response

```json
{
  "productId": 123,
  "originalImageUrl": "/api/products/123/image",
  "generatedImageUrl": "/api/products/123/image?t=1687245896325", 
  "prompt": "Change the model to be wearing sunglasses but keep the same clothing",
  "generatedAt": "2023-06-20T14:31:36.325Z",
  "status": "SUCCESS",
  "message": "Image generated successfully"
}
```

## Error Handling

The API returns appropriate HTTP status codes:

- `200 OK`: Image generated successfully
- `400 Bad Request`: Invalid input parameters
- `401 Unauthorized`: Missing or invalid authentication token
- `403 Forbidden`: Not authorized to modify this product
- `404 Not Found`: Product not found
- `500 Internal Server Error`: Server-side error during processing

Error responses follow the same format as successful responses, but with a status of "ERROR" and details in the message field.

## Notes

1. The API automatically updates the product with the new generated image
2. When the API returns successfully, the product's image has already been updated
3. The original image is not preserved in the database once replaced

## Rate Limiting

To prevent abuse, this API is subject to the following rate limits:

- Maximum 10 requests per minute per user
- Maximum 100 requests per day per user 