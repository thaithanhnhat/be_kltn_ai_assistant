package com.g18.assistant.service.impl;

import com.g18.assistant.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.core.io.ClassPathResource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
    
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    
    @Value("${app.frontend.url}")
    private String frontendUrl;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    @Override
    @Async
    public CompletableFuture<Boolean> sendVerificationEmail(String to, String verificationToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Email Verification");
            
            Context context = new Context();
            String encodedEmail = URLEncoder.encode(to, StandardCharsets.UTF_8.toString());
            String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken + 
                                     "&email=" + encodedEmail + "&mode=register";
            context.setVariable("verificationLink", verificationLink);
            
            // Add logo CID reference
            context.setVariable("logoResourceName", "logo");
            
            String htmlContent = templateEngine.process("email-verification", context);
            helper.setText(htmlContent, true);
            
            // Add the inline image, referenced from the HTML code as "cid:logo"
            ClassPathResource imageResource = new ClassPathResource("static/logo.png");
            if (imageResource.exists()) {
                helper.addInline("logo", imageResource);
            }
            
            mailSender.send(message);
            log.info("Verification email sent to: {}", to);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", to, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    @Async
    public CompletableFuture<Boolean> sendPasswordResetEmail(String to, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Password Reset Request");
            
            Context context = new Context();
            String encodedEmail = URLEncoder.encode(to, StandardCharsets.UTF_8.toString());
            String resetLink = frontendUrl + "/verify-email?token=" + resetToken + 
                              "&email=" + encodedEmail + "&mode=reset";
            context.setVariable("resetLink", resetLink);
            
            // Add logo CID reference
            context.setVariable("logoResourceName", "logo");
            
            String htmlContent = templateEngine.process("password-reset", context);
            helper.setText(htmlContent, true);
            
            // Add the inline image, referenced from the HTML code as "cid:logo"
            ClassPathResource imageResource = new ClassPathResource("static/logo.png");
            if (imageResource.exists()) {
                helper.addInline("logo", imageResource);
            }
            
            mailSender.send(message);
            log.info("Password reset email sent to: {}", to);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
} 