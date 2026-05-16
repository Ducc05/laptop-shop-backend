package com.laptopshop.controller;

import com.laptopshop.dto.PaymentResponse;
import com.laptopshop.entity.PaymentMethod;
import com.laptopshop.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/create-url/{orderId}")
    public ResponseEntity<PaymentResponse> createPaymentUrl(
            @PathVariable Long orderId,
            @RequestParam PaymentMethod method,
            HttpServletRequest request) throws Exception {
        
        String url = paymentService.createPaymentUrl(orderId, method, request);
        return ResponseEntity.ok(PaymentResponse.builder()
                .paymentUrl(url)
                .status("OK")
                .message("Success")
                .build());
    }

    @GetMapping("/vnpay-callback")
    public ResponseEntity<Void> vnpayCallback(@RequestParam Map<String, String> params) {
        paymentService.processVnPayCallback(params);
        String orderId = "";
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef != null && !txnRef.isBlank()) {
            orderId = "&orderId=" + txnRef.split("_")[0];
        }

        return ResponseEntity.status(302)
                .header("Location", "http://localhost:3000/payment/result?status=" + params.get("vnp_ResponseCode") + orderId)
                .build();
    }

    @PostMapping("/momo-callback")
    public ResponseEntity<Void> momoCallback(@RequestBody Map<String, String> params) {
        paymentService.processMomoCallback(params);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/zalopay-callback")
    public ResponseEntity<Map<String, Object>> zalopayCallback(@RequestBody Map<String, Object> body) {
        paymentService.processZaloPayCallback(body);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("return_code", 1);
        result.put("return_message", "success");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stripe-success")
    public ResponseEntity<Void> stripeSuccess(
            @RequestParam Long orderId,
            @RequestParam String sessionId) throws Exception {
        paymentService.processStripeSuccess(orderId, sessionId);
        return ResponseEntity.status(302)
                .header("Location", "http://localhost:3000/payment/result?status=SUCCESS&orderId=" + orderId)
                .build();
    }

    @GetMapping("/stripe-cancel")
    public ResponseEntity<Void> stripeCancel(@RequestParam Long orderId) {
        paymentService.processPaymentCancel(orderId);
        return ResponseEntity.status(302)
                .header("Location", "http://localhost:3000/payment/result?status=CANCELLED&orderId=" + orderId)
                .build();
    }
}
