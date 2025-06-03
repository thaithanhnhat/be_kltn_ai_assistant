# Test Plan: Order Creation Fix

## Problem Statement
When customers provide their address after being asked for order placement, the AI detects `ADDRESS_RESPONSE` intent correctly but fails to create the order because:
1. AI doesn't include `create_order: true` in response
2. AI doesn't include `action_details` with `action_type: 'PLACEORDER'`, `product_id`, and `quantity`
3. Fallback logic runs after `needs_shop_context` check, so pending orders aren't processed

## Changes Made

### 1. Enhanced AI Prompt for ADDRESS_RESPONSE
- Added specific instructions for AI to include `create_order: true` when address is for an order
- Added requirement to include `action_details` with complete order information
- Added concrete example showing exact JSON structure required

### 2. Fixed Logic Flow
- Moved pending order fallback logic BEFORE `needs_shop_context` check
- Ensures pending orders are processed even when AI returns incomplete information

## Test Scenario

### Step 1: Customer wants to order
**Input:** "mih muốn đặt 5 chai coca"
**Expected:** AI asks for address, stores pending order

### Step 2: Customer provides address
**Input:** "mih ở 77 nguyễn huệ, thành phố huế. sdt của mih là 0327538428"
**Expected AI Response Should Include:**
```json
{
  "detected_intent": "ADDRESS_RESPONSE",
  "extracted_address": "77 nguyễn huệ, thành phố huế",
  "extracted_phone": "0327538428",
  "action_required": true,
  "create_order": true,
  "action_details": {
    "action_type": "PLACEORDER",
    "product_id": 1,
    "quantity": 5
  },
  "needs_shop_context": false
}
```

### Step 3: System should create order
**Expected:**
1. If AI includes `create_order: true` and `action_details` → Order created directly
2. If AI doesn't include complete info → Fallback logic creates order from pending request
3. Order appears in database with status PENDING
4. Customer address updated
5. Pending order removed from cache

## Success Criteria
- ✅ Order is created regardless of AI response completeness
- ✅ Customer address is updated
- ✅ Customer phone is updated (if provided)
- ✅ Pending order is removed after successful creation
- ✅ Proper logging shows order creation path taken

## Verification
1. Check database for new order entry
2. Check customer record for updated address/phone
3. Check logs for "Created order from..." messages
4. Verify pending order cache is cleared
