POST /api/tablet/auth/request-otp/
Content-Type: application/json

{
"email": "dispatcher@example.com"
}

reponse
{
"status": "success",
"data": {
"email": "dispatcher@example.com",
"expires_in": 600,
"resend_after": 60,
"app_version": "2.3.4",
"apk_url": "https://your-domain.com/downloads/tablet-2.3.4.apk"
},
"message": "Verification code sent to the user's email.",
"errors": null
}

----------------

POST /api/tablet/auth/verify-otp/
Content-Type: application/json

{
"email": "dispatcher@example.com",
"code": "123456",
"device_id": "8f57eb16-2433-4ee5-a25d-b4801f58ca89"
}

response

{
"status": "success",
"data": {
"tokens": {
"access": "<access-token>",
"refresh": "<refresh-token>"
},
"user": {
"id": 42,
"username": "dispatcher",
"email": "dispatcher@example.com",
"name": "John Dispatcher",
"role": "dispatcher",
"company_id": 5
},
"device_id": "8f57eb16-2433-4ee5-a25d-b4801f58ca89",
"client_type": "tablet",
"app_version": "2.3.4",
"apk_url": "https://your-domain.com/downloads/tablet-2.3.4.apk"
},
"message": "Tablet login verified successfully.",
"errors": null
}

-------
GET /api/tablet/auth/me/
Authorization: Bearer <access-token>
response

{
"status": "success",
"data": {
"id": 42,
"username": "dispatcher",
"email": "dispatcher@example.com",
"name": "John Dispatcher",
"role": "dispatcher",
"company_id": 5,
"client_type": "tablet",
"app_version": "2.3.4",
"apk_url": "https://your-domain.com/downloads/tablet-2.3.4.apk"
},
"message": "Authenticated tablet user details.",
"errors": null
}

----------

POST /api/tablet/location/
Authorization: Bearer <access-token>
Content-Type: application/json

{
"device_id": "8f57eb16-2433-4ee5-a25d-b4801f58ca89",
"latitude": 43.8561,
"longitude": -79.337,
"heading": 87.5,
"speed": 12.3
}

resopnse

{
"status": "success",
"data": {
"device_id": "8f57eb16-2433-4ee5-a25d-b4801f58ca89"
},
"message": "Tablet location updated.",
"errors": null
}

_---------------------

GET /api/tablet/app/version/

response (flat, not enveloped)

{
"version": "1.0.1",
"download_url": "https://api.quadrix.ai/api/tablet/app/download/"
}

_---------------------

POST /api/token/refresh/
Content-Type: application/json

{
"refresh": "<refresh-token>"
}

-----------------------
GET /api/tablet/app/version/


{
"version": "1.0.1",
"download_url": "https://api.quadrix.ai/api/tablet/app/download/"
}