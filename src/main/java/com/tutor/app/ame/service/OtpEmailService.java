package com.tutor.app.ame.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OtpEmailService {

    private final String sendGridApiKey;
    private final String fromEmail;
    private final String fromName;

    public OtpEmailService(
            @Value("${sendgrid.api-key:}") String sendGridApiKey,
            @Value("${ame.otp.from-email:}") String fromEmail,
            @Value("${ame.otp.from-name:AME}") String fromName
    ) {
        this.sendGridApiKey = sendGridApiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    public void sendOtp(String toEmail, String otpCode) {
        if (sendGridApiKey == null || sendGridApiKey.isBlank()) {
            throw new IllegalStateException("SendGrid API key is not configured.");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("OTP sender email is not configured.");
        }

        Email from = new Email(fromEmail, fromName);
        Email to = new Email(toEmail);
        String subject = "Your AME one-time passcode";
        String body = "Your OTP is " + otpCode + ". It expires in 10 minutes.";
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, to, content);

        try {
            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            int status = response.getStatusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Failed to send OTP email. Provider status: " + status);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send OTP email.", ex);
        }
    }
}
