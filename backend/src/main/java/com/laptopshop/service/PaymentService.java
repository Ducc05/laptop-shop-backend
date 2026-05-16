package com.laptopshop.service;

import com.laptopshop.entity.Order;
import com.laptopshop.entity.Payment;
import com.laptopshop.entity.PaymentMethod;
import com.laptopshop.entity.PaymentStatus;
import com.laptopshop.entity.OrderStatus;
import com.laptopshop.repository.OrderRepository;
import com.laptopshop.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EmailService emailService;

    @Value("${vnpay.tmn-code:TC8H9799}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret:A58SORY89SDRSSTWID7S61Y67332REK2}")
    private String vnpHashSecret;

    @Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpUrl;

    @Value("${vnpay.return-url:http://localhost:8080/api/v1/payments/vnpay-callback}")
    private String vnpReturnUrl;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${momo.partner-code:MOMO}")
    private String momoPartnerCode;

    @Value("${momo.access-key:ACCESS_KEY}")
    private String momoAccessKey;

    @Value("${momo.secret-key:SECRET_KEY}")
    private String momoSecretKey;

    @Value("${momo.url:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String momoUrl;

    @Value("${zalopay.appid:2553}")
    private String zaloAppId;

    @Value("${zalopay.key1:PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL}")
    private String zaloKey1;

    @Value("${zalopay.key2:kLtgPl8YEStV641V4PInSQkdbf4xXz}")
    private String zaloKey2;

    @Value("${zalopay.url:https://sb-openapi.zalopay.vn/v2/create}")
    private String zaloUrl;

    @Value("${app.payment.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Transactional
    public String createPaymentUrl(Long orderId, PaymentMethod method, HttpServletRequest request) throws Exception {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setPaymentMethod(method);
        order.setPaymentStatus(PaymentStatus.PENDING);

        // Lấy số tiền thật của đơn hàng
        long amount = order.getTotalPrice().longValue();

        return switch (method) {
            case VNPAY -> createVnPayUrl(order, amount, request);
            case MOMO -> createMomoUrl(order, amount);
            case ZALOPAY -> createZaloPayUrl(order, amount);
            case STRIPE -> createStripeUrl(order, amount);
            default -> throw new RuntimeException("Unsupported payment method: " + method);
        };
    }

    private String createVnPayUrl(Order order, long amount, HttpServletRequest request) throws Exception {
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_TxnRef = order.getId().toString() + "_" + System.currentTimeMillis();
        String vnp_IpAddr = "127.0.0.1";
        String vnp_TmnCode = vnpTmnCode;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100)); // VNPAY đơn vị là xu
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang: " + order.getId());
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnpReturnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(java.net.URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                // Build query
                query.append(java.net.URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                query.append('=');
                query.append(java.net.URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        String queryUrl = query.toString();
        String vnp_SecureHash = hmacSHA512(vnpHashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        return vnpUrl + "?" + queryUrl;
    }

    private String createMomoUrl(Order order, long amount) throws Exception {
        String requestId = String.valueOf(System.currentTimeMillis());
        String orderIdStr = order.getId() + "_" + requestId;
        String orderInfo = "Thanh toan don hang " + order.getId();
        String redirectUrl = "http://localhost:3000/payment/result";
        String ipnUrl = backendUrl + "/api/v1/payments/momo-callback";
        String requestType = "captureWallet";
        String extraData = "";

        String rawHash = "accessKey=" + momoAccessKey +
                "&amount=" + amount +
                "&extraData=" + extraData +
                "&ipnUrl=" + ipnUrl +
                "&orderId=" + orderIdStr +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + momoPartnerCode +
                "&redirectUrl=" + redirectUrl +
                "&requestId=" + requestId +
                "&requestType=" + requestType;

        String signature = hmacSHA256(momoSecretKey, rawHash);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("partnerCode", momoPartnerCode);
        requestBody.put("partnerName", "LaptopShop");
        requestBody.put("storeId", "LaptopShop");
        requestBody.put("requestId", requestId);
        requestBody.put("amount", amount);
        requestBody.put("orderId", orderIdStr);
        requestBody.put("orderInfo", orderInfo);
        requestBody.put("redirectUrl", redirectUrl);
        requestBody.put("ipnUrl", ipnUrl);
        requestBody.put("lang", "vi");
        requestBody.put("requestType", requestType);
        requestBody.put("extraData", extraData);
        requestBody.put("signature", signature);

        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        Map<String, Object> response = restTemplate.postForObject(momoUrl, requestBody, Map.class);
        
        if (response != null && response.get("payUrl") != null) {
            return (String) response.get("payUrl");
        }
        throw new RuntimeException("Momo creation failed: " + response);
    }

    private String createZaloPayUrl(Order order, long amount) throws Exception {
        String app_time = String.valueOf(System.currentTimeMillis());
        String app_trans_id = new java.text.SimpleDateFormat("yyMMdd").format(new Date()) + "_" + order.getId() + "_" + System.currentTimeMillis();
        String app_user = "laptopshop_user";
        String item = "[]";
        String description = "Thanh toan don hang #" + order.getId();
        String embed_data = "{\"redirecturl\":\"http://localhost:3000/payment/result\"}";
        String bank_code = "";
        String callback_url = backendUrl + "/api/v1/payments/zalopay-callback";

        String data = zaloAppId + "|" + app_trans_id + "|" + app_user + "|" + amount + "|" + app_time + "|" + embed_data + "|" + item;
        String mac = hmacSHA256(zaloKey1, data);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("app_id", zaloAppId);
        requestBody.put("app_user", app_user);
        requestBody.put("app_time", app_time);
        requestBody.put("amount", amount);
        requestBody.put("app_trans_id", app_trans_id);
        requestBody.put("bank_code", bank_code);
        requestBody.put("embed_data", embed_data);
        requestBody.put("item", item);
        requestBody.put("description", description);
        requestBody.put("callback_url", callback_url);
        requestBody.put("mac", mac);

        // ZaloPay API expects form-urlencoded, but JSON often works in v2. Let's send x-www-form-urlencoded
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        org.springframework.util.MultiValueMap<String, String> map = new org.springframework.util.LinkedMultiValueMap<>();
        requestBody.forEach((k, v) -> map.add(k, String.valueOf(v)));

        org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new org.springframework.http.HttpEntity<>(map, headers);
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        
        Map<String, Object> response = restTemplate.postForObject(zaloUrl, request, Map.class);
        
        if (response != null && response.get("order_url") != null) {
            return (String) response.get("order_url");
        }
        throw new RuntimeException("ZaloPay creation failed: " + response);
    }

    private String createStripeUrl(Order order, long amount) throws Exception {
        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(backendUrl + "/api/v1/payments/stripe-success?orderId=" + order.getId() + "&sessionId={CHECKOUT_SESSION_ID}")
                .setCancelUrl(backendUrl + "/api/v1/payments/stripe-cancel?orderId=" + order.getId())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("vnd")
                                .setUnitAmount(amount) // Stripe VND dùng đơn vị thật (không nhân 100)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Thanh toan don hang #" + order.getId())
                                        .build())
                                .build())
                        .build())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    @Transactional
    public void processVnPayCallback(Map<String, String> params) {
        String vnp_ResponseCode = params.get("vnp_ResponseCode");
        String vnp_TxnRef = params.get("vnp_TxnRef");
        Long orderId = Long.parseLong(vnp_TxnRef.split("_")[0]);

        Order order = orderRepository.findById(orderId).orElseThrow();
        
        if ("00".equals(vnp_ResponseCode)) {
            handlePaymentSuccess(order, params.get("vnp_TransactionNo"));
        } else if ("24".equals(vnp_ResponseCode)) {
            handlePaymentCancelled(order, params.get("vnp_TransactionNo"));
        } else {
            handlePaymentFailure(order, params.get("vnp_TransactionNo"));
        }
        orderRepository.save(order);
    }

    @Transactional
    public void processMomoCallback(Map<String, String> params) {
        // Kiểm tra signature ở đây nếu cần bảo mật cao hơn
        String resultCode = params.get("resultCode");
        String orderIdStr = params.get("orderId");
        Long orderId = Long.parseLong(orderIdStr.split("_")[0]);
        String transId = params.get("transId");

        Order order = orderRepository.findById(orderId).orElseThrow();

        if ("0".equals(resultCode)) {
            handlePaymentSuccess(order, transId);
        } else if ("1006".equals(resultCode)) {
            handlePaymentCancelled(order, transId);
        } else {
            handlePaymentFailure(order, transId);
        }
        orderRepository.save(order);
    }

    @Transactional
    public void processPaymentCancel(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        handlePaymentCancelled(order, null);
        orderRepository.save(order);
    }

    @Transactional
    public void processZaloPayCallback(Map<String, Object> body) {
        // ZaloPay gửi callback dạng POST với JSON chứa data và mac
        // Ở mức độ đơn giản, ta lấy data ra để xử lý
        try {
            String dataStr = (String) body.get("data");
            // Parse data JSON string
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> data = mapper.readValue(dataStr, Map.class);
            
            String appTransId = (String) data.get("app_trans_id");
            Long orderId = Long.parseLong(appTransId.split("_")[1]);
            String zpTransId = String.valueOf(data.get("zp_trans_id"));

            Order order = orderRepository.findById(orderId).orElseThrow();
            handlePaymentSuccess(order, zpTransId);
            orderRepository.save(order);
        } catch (Exception e) {
            throw new RuntimeException("ZaloPay callback processing failed", e);
        }
    }

    @Transactional
    public void processStripeSuccess(Long orderId, String sessionId) throws Exception {
        Stripe.apiKey = stripeSecretKey;
        Session session = Session.retrieve(sessionId);

        Order order = orderRepository.findById(orderId).orElseThrow();

        if ("paid".equals(session.getPaymentStatus())) {
            handlePaymentSuccess(order, session.getPaymentIntent());
        } else {
            handlePaymentFailure(order, null);
        }
        orderRepository.save(order);
    }

    private void handlePaymentSuccess(Order order, String transactionId) {
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setStatus(OrderStatus.CONFIRMED); // Tự động xác nhận đơn hàng khi đã thanh toán
        updatePayment(order, PaymentStatus.SUCCESS, transactionId);

        // Gửi email thông báo cho khách hàng
        try {
            emailService.sendOrderSuccessEmail(order.getUser().getEmail(), order.getId(), order.getTotalPrice());
        } catch (Exception e) {
            // Log error but don't fail the payment process
            System.err.println("Failed to send success email: " + e.getMessage());
        }
    }

    private void handlePaymentFailure(Order order, String transactionId) {
        if (order.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return;
        }

        order.setPaymentStatus(PaymentStatus.FAILED);
        // Có thể giữ OrderStatus là PENDING để khách thử lại hoặc chuyển thành FAILED
        updatePayment(order, PaymentStatus.FAILED, transactionId);
    }

    private void handlePaymentCancelled(Order order, String transactionId) {
        if (order.getPaymentStatus() == PaymentStatus.SUCCESS || order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        order.setPaymentStatus(PaymentStatus.CANCELLED);
        updatePayment(order, PaymentStatus.CANCELLED, transactionId);
    }

    private void updatePayment(Order order, PaymentStatus status, String transactionId) {
        // Luôn tạo bản ghi Payment mới để lưu lịch sử các lần thử thanh toán
        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getTotalPrice())
                .method(order.getPaymentMethod())
                .status(status)
                .transactionId(transactionId)
                .build();
        
        paymentRepository.save(payment);
    }

    public String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    public String hmacSHA256(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final Mac hmac256 = Mac.getInstance("HmacSHA256");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
            hmac256.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac256.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
