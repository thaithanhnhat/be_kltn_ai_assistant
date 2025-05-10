# Order Management API Documentation

## Overview

This API provides functionality for managing customers, orders, and feedback for a shop. It allows shop owners to manage their customer base, track orders, and collect feedback on products.

## Base URL

```
https://api.example.com
```

## Authentication

All API requests require authentication using Bearer token.

```
Authorization: Bearer {access_token}
```

## API Endpoints

### Customer Management

#### Create a New Customer

```
POST /api/customers
```

Creates a new customer for a shop.

**Request Body**:

```json
{
  "shopId": 1,
  "fullname": "John Doe",
  "address": "123 Main St, City",
  "phone": "+1234567890",
  "email": "john.doe@example.com"
}
```

**Response**: `201 Created`

```json
{
  "id": 1,
  "shopId": 1,
  "fullname": "John Doe",
  "address": "123 Main St, City",
  "phone": "+1234567890",
  "email": "john.doe@example.com",
  "createdAt": "2023-07-15T14:30:45.123Z"
}
```

#### Get Customer by ID

```
GET /api/customers/{id}
```

Retrieves a specific customer by their ID.

**Response**: `200 OK`

```json
{
  "id": 1,
  "shopId": 1,
  "fullname": "John Doe",
  "address": "123 Main St, City",
  "phone": "+1234567890",
  "email": "john.doe@example.com",
  "createdAt": "2023-07-15T14:30:45.123Z"
}
```

#### Get Customers by Shop ID

```
GET /api/customers/shop/{shopId}
```

Retrieves all customers belonging to a specific shop.

**Response**: `200 OK`

```json
[
  {
    "id": 1,
    "shopId": 1,
    "fullname": "John Doe",
    "address": "123 Main St, City",
    "phone": "+1234567890",
    "email": "john.doe@example.com",
    "createdAt": "2023-07-15T14:30:45.123Z"
  },
  {
    "id": 2,
    "shopId": 1,
    "fullname": "Jane Smith",
    "address": "456 Oak Ave, Town",
    "phone": "+9876543210",
    "email": "jane.smith@example.com",
    "createdAt": "2023-07-16T09:15:30.456Z"
  }
]
```

#### Update Customer

```
PUT /api/customers/{id}
```

Updates an existing customer's information.

**Request Body**:

```json
{
  "shopId": 1,
  "fullname": "John Doe Updated",
  "address": "789 Pine St, City",
  "phone": "+1234567890",
  "email": "john.doe@example.com"
}
```

**Response**: `200 OK`

```json
{
  "id": 1,
  "shopId": 1,
  "fullname": "John Doe Updated",
  "address": "789 Pine St, City",
  "phone": "+1234567890",
  "email": "john.doe@example.com",
  "createdAt": "2023-07-15T14:30:45.123Z"
}
```

#### Delete Customer

```
DELETE /api/customers/{id}
```

Deletes a customer from the system.

**Response**: `204 No Content`

### Order Management

#### Create a New Order

```
POST /api/orders
```

Creates a new order for a customer.

**Request Body**:

```json
{
  "customerId": 1,
  "productId": 2,
  "note": "Please gift wrap",
  "quantity": 2,
  "deliveryUnit": "Express Shipping"
}
```

**Response**: `201 Created`

```json
{
  "id": 1,
  "customerId": 1,
  "customerName": "John Doe",
  "productId": 2,
  "productName": "Product Name",
  "note": "Please gift wrap",
  "quantity": 2,
  "deliveryUnit": "Express Shipping",
  "status": "PENDING",
  "createdAt": "2023-07-17T10:20:30.789Z"
}
```

#### Get Order by ID

```
GET /api/orders/{id}
```

Retrieves a specific order by its ID.

**Response**: `200 OK`

```json
{
  "id": 1,
  "customerId": 1,
  "customerName": "John Doe",
  "productId": 2,
  "productName": "Product Name",
  "note": "Please gift wrap",
  "quantity": 2,
  "deliveryUnit": "Express Shipping",
  "status": "PENDING",
  "createdAt": "2023-07-17T10:20:30.789Z"
}
```

#### Get Orders by Shop ID

```
GET /api/orders/shop/{shopId}
```

Retrieves all orders for a specific shop.

**Response**: `200 OK`

```json
[
  {
    "id": 1,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 2,
    "productName": "Product Name",
    "note": "Please gift wrap",
    "quantity": 2,
    "deliveryUnit": "Express Shipping",
    "status": "PENDING",
    "createdAt": "2023-07-17T10:20:30.789Z"
  },
  {
    "id": 2,
    "customerId": 2,
    "customerName": "Jane Smith",
    "productId": 3,
    "productName": "Another Product",
    "note": "",
    "quantity": 1,
    "deliveryUnit": "Standard Shipping",
    "status": "CONFIRMED",
    "createdAt": "2023-07-18T11:22:33.444Z"
  }
]
```

#### Get Orders by Customer ID

```
GET /api/orders/customer/{customerId}
```

Retrieves all orders for a specific customer.

**Response**: `200 OK`

```json
[
  {
    "id": 1,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 2,
    "productName": "Product Name",
    "note": "Please gift wrap",
    "quantity": 2,
    "deliveryUnit": "Express Shipping",
    "status": "PENDING",
    "createdAt": "2023-07-17T10:20:30.789Z"
  },
  {
    "id": 3,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 4,
    "productName": "Third Product",
    "note": "",
    "quantity": 3,
    "deliveryUnit": "Standard Shipping",
    "status": "SHIPPING",
    "createdAt": "2023-07-19T14:15:16.789Z"
  }
]
```

#### Get Orders by Status

```
GET /api/orders/shop/{shopId}/status/{status}
```

Retrieves all orders for a specific shop with a specific status.

**Available Statuses**: `PENDING`, `CONFIRMED`, `SHIPPING`, `DELIVERED`, `CANCELLED`, `RETURNED`

**Response**: `200 OK`

```json
[
  {
    "id": 1,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 2,
    "productName": "Product Name",
    "note": "Please gift wrap",
    "quantity": 2,
    "deliveryUnit": "Express Shipping",
    "status": "PENDING",
    "createdAt": "2023-07-17T10:20:30.789Z"
  }
]
```

#### Get Orders by Date Range

```
GET /api/orders/shop/{shopId}/date-range?startDate={startDate}&endDate={endDate}
```

Retrieves all orders for a specific shop within a date range.

**Parameters**:
- `startDate`: The start date (ISO format: `YYYY-MM-DDTHH:MM:SS`)
- `endDate`: The end date (ISO format: `YYYY-MM-DDTHH:MM:SS`)

**Example**:
```
GET /api/orders/shop/1/date-range?startDate=2023-07-01T00:00:00&endDate=2023-07-31T23:59:59
```

**Response**: `200 OK`

```json
[
  {
    "id": 1,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 2,
    "productName": "Product Name",
    "note": "Please gift wrap",
    "quantity": 2,
    "deliveryUnit": "Express Shipping",
    "status": "PENDING",
    "createdAt": "2023-07-17T10:20:30.789Z"
  },
  {
    "id": 2,
    "customerId": 2,
    "customerName": "Jane Smith",
    "productId": 3,
    "productName": "Another Product",
    "note": "",
    "quantity": 1,
    "deliveryUnit": "Standard Shipping",
    "status": "CONFIRMED",
    "createdAt": "2023-07-18T11:22:33.444Z"
  }
]
```

#### Update Order Status

```
PATCH /api/orders/{id}/status
```

Updates the status of an existing order.

**Request Body**:

```json
{
  "status": "CONFIRMED"
}
```

**Response**: `200 OK`

```json
{
  "id": 1,
  "customerId": 1,
  "customerName": "John Doe",
  "productId": 2,
  "productName": "Product Name",
  "note": "Please gift wrap",
  "quantity": 2,
  "deliveryUnit": "Express Shipping",
  "status": "CONFIRMED",
  "createdAt": "2023-07-17T10:20:30.789Z"
}
```

#### Delete Order

```
DELETE /api/orders/{id}
```

Deletes an order from the system.

**Response**: `204 No Content`

### Feedback Management

#### Create a New Feedback

```
POST /api/feedbacks
```

Creates a new feedback for a product from a customer.

**Request Body**:

```json
{
  "customerId": 1,
  "productId": 2,
  "content": "Great product! Excellent quality and fast delivery."
}
```

**Response**: `201 Created`

```json
{
  "id": 1,
  "customerId": 1,
  "customerName": "John Doe",
  "productId": 2,
  "productName": "Product Name",
  "content": "Great product! Excellent quality and fast delivery.",
  "time": "2023-07-20T15:30:45.678Z"
}
```

#### Get Feedback by ID

```
GET /api/feedbacks/{id}
```

Retrieves a specific feedback by its ID.

**Response**: `200 OK`

```json
{
  "id": 1,
  "customerId": 1,
  "customerName": "John Doe",
  "productId": 2,
  "productName": "Product Name",
  "content": "Great product! Excellent quality and fast delivery.",
  "time": "2023-07-20T15:30:45.678Z"
}
```

#### Get Feedbacks by Shop ID

```
GET /api/feedbacks/shop/{shopId}
```

Retrieves all feedbacks for a specific shop.

**Response**: `200 OK`

```json
[
  {
    "id": 1,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 2,
    "productName": "Product Name",
    "content": "Great product! Excellent quality and fast delivery.",
    "time": "2023-07-20T15:30:45.678Z"
  },
  {
    "id": 2,
    "customerId": 2,
    "customerName": "Jane Smith",
    "productId": 3,
    "productName": "Another Product",
    "content": "Good product but delivery was delayed.",
    "time": "2023-07-21T09:10:11.222Z"
  }
]
```

#### Get Feedbacks by Customer ID

```
GET /api/feedbacks/customer/{customerId}
```

Retrieves all feedbacks from a specific customer.

**Response**: `200 OK`

```json
[
  {
    "id": 1,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 2,
    "productName": "Product Name",
    "content": "Great product! Excellent quality and fast delivery.",
    "time": "2023-07-20T15:30:45.678Z"
  },
  {
    "id": 3,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 4,
    "productName": "Third Product",
    "content": "Average product, could be better.",
    "time": "2023-07-22T14:15:16.333Z"
  }
]
```

#### Get Feedbacks by Product ID

```
GET /api/feedbacks/product/{productId}
```

Retrieves all feedbacks for a specific product, sorted by most recent first.

**Response**: `200 OK`

```json
[
  {
    "id": 1,
    "customerId": 1,
    "customerName": "John Doe",
    "productId": 2,
    "productName": "Product Name",
    "content": "Great product! Excellent quality and fast delivery.",
    "time": "2023-07-20T15:30:45.678Z"
  },
  {
    "id": 4,
    "customerId": 3,
    "customerName": "Alice Johnson",
    "productId": 2,
    "productName": "Product Name",
    "content": "Very happy with this purchase!",
    "time": "2023-07-18T13:14:15.444Z"
  }
]
```

#### Delete Feedback

```
DELETE /api/feedbacks/{id}
```

Deletes a feedback from the system.

**Response**: `204 No Content`

## Error Responses

### 400 Bad Request

Returned when the request is invalid or contains incorrect parameters.

```json
{
  "timestamp": "2023-07-20T10:15:30.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": [
    "Email must be valid",
    "Full name is required"
  ],
  "path": "/api/customers"
}
```

### 401 Unauthorized

Returned when authentication fails or credentials are missing.

```json
{
  "timestamp": "2023-07-20T10:15:30.123Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required",
  "path": "/api/customers"
}
```

### 403 Forbidden

Returned when the authenticated user doesn't have permission to access the resource.

```json
{
  "timestamp": "2023-07-20T10:15:30.123Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied",
  "path": "/api/orders/shop/2"
}
```

### 404 Not Found

Returned when the requested resource doesn't exist.

```json
{
  "timestamp": "2023-07-20T10:15:30.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Customer not found with id: 999",
  "path": "/api/customers/999"
}
```

### 409 Conflict

Returned when there's a conflict with the current state of the resource.

```json
{
  "timestamp": "2023-07-20T10:15:30.123Z",
  "status": 409,
  "error": "Conflict",
  "message": "Customer with email john.doe@example.com already exists in this shop",
  "path": "/api/customers"
}
```

### 500 Internal Server Error

Returned when an unexpected server error occurs.

```json
{
  "timestamp": "2023-07-20T10:15:30.123Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "path": "/api/orders"
}
```

## Business Rules

### Order Status Flow

1. When an order is created, its initial status is `PENDING`.
2. Order status can be updated through the `PATCH /api/orders/{id}/status` endpoint.
3. When an order's status is changed to `CANCELLED`, the product stock is automatically restored.
4. When changing from `CANCELLED` to `CONFIRMED`, the system checks if there is sufficient stock.

### Product Stock Management

1. When creating an order, the system checks if there's enough stock available.
2. If sufficient stock exists, the product stock is reduced by the order quantity.
3. If an order is cancelled, the stock is restored.
4. When deleting an order that is not cancelled, the stock is restored.

### Customer Uniqueness

1. A customer's email must be unique within a shop.
2. A customer's phone must be unique within a shop.
3. Updating a customer will check that the updated information does not conflict with other customers in the same shop.

## FAQ

### Q: How do I handle pagination for large result sets?

Currently, the API does not support pagination. For large shops, we recommend implementing client-side filtering and limiting queries by date ranges to avoid performance issues.

### Q: How can I implement search functionality for customers?

Custom search functionality is planned for a future release. Currently, you can retrieve all customers for a shop and implement search on the client side.

### Q: Are there rate limits on the API?

Yes, the API has rate limiting in place to ensure service quality. Most endpoints are limited to 100 requests per minute per API key.

### Q: How should I test the API?

Use the sandbox environment first (`https://sandbox-api.example.com`) with test credentials before integrating with the production API.

### Q: What should I do if I need to update multiple orders at once?

Currently, the API only supports updating orders individually. For bulk operations, make sequential API calls.

---

For additional support, please contact support@example.com 