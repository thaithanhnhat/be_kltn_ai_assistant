# Product Management API Documentation

## Overview

The Product Management API allows shop owners to manage their product inventory and enables customers to browse and search for products. It includes special endpoints optimized for AI consultation.

## Base URL

```
http://localhost:8080/assistant/api
```

## Authentication

Most endpoints require authentication using JWT (JSON Web Token). Include the token in the `Authorization` header:

```
Authorization: Bearer <token>
```

## Endpoints

### Public Endpoints

#### Get All Products

Returns a paginated list of all active products.

```
GET /products
```

**Query Parameters:**

| Parameter | Type   | Default | Description |
|-----------|--------|---------|-------------|
| page      | int    | 0       | Page number (zero-based) |
| size      | int    | 10      | Page size |
| keyword   | string | null    | Search keyword for product name and description |
| category  | string | null    | Filter by category |
| sortBy    | string | "id"    | Field to sort by |
| sortDir   | string | "asc"   | Sort direction ("asc" or "desc") |

**Response:**

```json
{
  "content": [
    {
      "id": 1,
      "shopId": 1,
      "shopName": "Shop A",
      "name": "Product 1",
      "price": 100.00,
      "description": "Product description",
      "category": "Electronics",
      "stock": 10,
      "imageBase64": null,
      "customFields": {},
      "active": true,
      "createdAt": "2023-07-01T12:00:00",
      "updatedAt": "2023-07-01T12:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 100,
  "totalPages": 10,
  "last": false
}
```

#### Get Product by ID

Returns detailed information about a specific product.

```
GET /products/{productId}
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| productId | long | Product ID  |

**Response:**

```json
{
  "id": 1,
  "shopId": 1,
  "shopName": "Shop A",
  "name": "Product 1",
  "price": 100.00,
  "description": "Product description",
  "category": "Electronics",
  "stock": 10,
  "imageBase64": "base64EncodedImage",
  "customFields": {
    "color": "black",
    "weight": "250g"
  },
  "active": true,
  "createdAt": "2023-07-01T12:00:00",
  "updatedAt": "2023-07-01T12:00:00"
}
```

#### Get Shop Categories

Returns a list of distinct product categories available in a shop.

```
GET /shops/{shopId}/categories
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| shopId    | long | Shop ID     |

**Response:**

```json
[
  "Electronics",
  "Clothing",
  "Books"
]
```

### Shop Owner Endpoints

These endpoints require authentication and the user must be the owner of the shop.

#### Get Shop Products

Returns a paginated list of all products belonging to a shop.

```
GET /shops/{shopId}/products
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| shopId    | long | Shop ID     |

**Query Parameters:**

| Parameter | Type   | Default | Description |
|-----------|--------|---------|-------------|
| page      | int    | 0       | Page number (zero-based) |
| size      | int    | 10      | Page size |
| keyword   | string | null    | Search keyword for product name and description |
| sortBy    | string | "id"    | Field to sort by |
| sortDir   | string | "asc"   | Sort direction ("asc" or "desc") |

**Response:** Same as Get All Products

#### Get Shop Product by ID

Returns detailed information about a specific product in a shop.

```
GET /shops/{shopId}/products/{productId}
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| shopId    | long | Shop ID     |
| productId | long | Product ID  |

**Response:** Same as Get Product by ID

#### Create Product

Creates a new product for a shop.

```
POST /shops/{shopId}/products
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| shopId    | long | Shop ID     |

**Request Body:**

```json
{
  "name": "New Product",
  "price": 199.99,
  "description": "Product description",
  "category": "Electronics",
  "stock": 50,
  "imageBase64": "base64EncodedImage",
  "customFields": {
    "color": "silver",
    "weight": "300g"
  }
}
```

**Required Fields:**
- name
- price
- category
- stock

**Response:**

```json
{
  "id": 2,
  "shopId": 1,
  "shopName": "Shop A",
  "name": "New Product",
  "price": 199.99,
  "description": "Product description",
  "category": "Electronics",
  "stock": 50,
  "imageBase64": "base64EncodedImage",
  "customFields": {
    "color": "silver",
    "weight": "300g"
  },
  "active": true,
  "createdAt": "2023-07-02T12:00:00",
  "updatedAt": "2023-07-02T12:00:00"
}
```

#### Update Product

Updates an existing product.

```
PUT /shops/{shopId}/products/{productId}
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| shopId    | long | Shop ID     |
| productId | long | Product ID  |

**Request Body:** Same as Create Product

**Response:** Same as Create Product but with updated values

#### Delete Product

Soft deletes a product by setting its active flag to false.

```
DELETE /shops/{shopId}/products/{productId}
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| shopId    | long | Shop ID     |
| productId | long | Product ID  |

**Response:** No content (HTTP 204)

#### Update Product Stock

Updates the stock quantity of a product.

```
PATCH /shops/{shopId}/products/{productId}/stock?quantity={quantity}
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| shopId    | long | Shop ID     |
| productId | long | Product ID  |

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| quantity  | int  | The quantity to add (positive) or subtract (negative) from current stock |

**Response:** No content (HTTP 200)

### AI Consultation Endpoint

A specialized endpoint for retrieving product data optimized for AI consultation.

```
GET /shops/{shopId}/products/consultation
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| shopId    | long | Shop ID     |

**Response:**

```json
[
  {
    "id": 1,
    "name": "Product 1",
    "price": 100.00,
    "category": "Electronics",
    "description": "Product description",
    "stock": 10,
    "customFields": {
      "color": "black",
      "weight": "250g"
    }
  }
]
```

## Error Handling

The API returns appropriate HTTP status codes:

- 200: Success
- 201: Created
- 204: No Content
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 500: Internal Server Error

Error responses include a message:

```json
{
  "timestamp": "2023-07-02T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid input data",
  "path": "/api/shops/1/products"
}
```

## Using Image Upload

For product images, convert the image to Base64 format before sending in the request. Here's an example of how to do this in JavaScript:

```javascript
function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = error => reject(error);
  });
}

// Example usage
const imageFile = document.querySelector('input[type="file"]').files[0];
fileToBase64(imageFile).then(base64 => {
  // Include this base64 string in your API request
  const productData = {
    name: "Product with image",
    price: 299.99,
    category: "Electronics",
    stock: 10,
    imageBase64: base64
  };
  
  // Send to API...
});
```

## Custom Fields

The `customFields` property allows you to define arbitrary product attributes as key-value pairs. This is useful for storing product-specific details that aren't part of the standard product model.

Examples of custom fields:
- For clothing: size, color, material, etc.
- For electronics: specifications, warranty info, etc.
- For books: author, publisher, ISBN, etc.

These fields can be used by the AI consultation feature to provide more detailed product recommendations. 