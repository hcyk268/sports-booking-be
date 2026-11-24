# SePay onboarding IPN compatibility

SePay sandbox onboarding sent an authenticated test notification with `notification_type: PAYMENT_SUCCESS`, `transaction_type: CAPTURE`, and `order.user_agent: {}`. The previous DTO declared user_agent as String and rejected the payload with HTTP 400 / SEPAY_IPN_INVALID.

Represent optional user_agent metadata as JsonNode to accept both the documented string and the observed object. Financial field validation and IPN authentication remain unchanged.

The regression fixture preserves the observed structure with a documentation-only IP address. The controller regression checks HTTP 200 acknowledgement after inbox commit and REVIEW_REQUIRED status for PAYMENT_SUCCESS. This unsupported notification must not confirm a booking or credit a payment. The [payment verification report](payment-endpoints-2026-11-24.md) records the subsequent sandbox checkout and ORDER_PAID processing.
