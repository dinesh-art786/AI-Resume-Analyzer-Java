
package resume_analyzer.service;

import io.github.cdimascio.dotenv.Dotenv;

import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Properties;

@Service
public class EmailService {

    private final JavaMailSenderImpl mailSender;
    private final String senderEmail;
    private final String frontendUrl;

    public EmailService() {

        Dotenv dotenv = Dotenv.configure()
                .directory("../backend")
                .ignoreIfMissing()
                .load();

        String host = dotenv.get("SMTP_HOST");
        String port = dotenv.get("SMTP_PORT");
        senderEmail = dotenv.get("SMTP_EMAIL");
        String password = dotenv.get("SMTP_PASSWORD");
        frontendUrl = dotenv.get("FRONTEND_URL");

        if (host == null || port == null
                || senderEmail == null || password == null
                || frontendUrl == null) {
            throw new IllegalStateException(
                    "Missing SMTP configuration in backend/.env"
            );
        }

        mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(Integer.parseInt(port));
        mailSender.setUsername(senderEmail);
        mailSender.setPassword(password);
        mailSender.setDefaultEncoding("UTF-8");

        Properties properties = mailSender.getJavaMailProperties();

        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
    }

    public void sendPasswordResetEmail(
            String recipientEmail,
            String recipientName,
            String token) {

        try {
            String resetLink = frontendUrl.replaceAll("/+$", "")
                    + "/reset-password?token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(recipientEmail);
            helper.setSubject("Reset Your ResumeAI Password");

            String safeName = recipientName == null
                    ? "User"
                    : recipientName.replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;");

            String html = """
                    <div style="font-family:Arial,sans-serif;
                                max-width:600px;margin:auto;
                                padding:30px;color:#333;">
                        <h2 style="color:#b82035;">ResumeAI</h2>
                        <h3>Password Reset Request</h3>
                        <p>Hello %s,</p>
                        <p>We received a request to reset your ResumeAI
                           account password.</p>
                        <p>
                            <a href="%s"
                               style="display:inline-block;
                                      background:#b82035;
                                      color:white;
                                      padding:13px 24px;
                                      text-decoration:none;
                                      border-radius:8px;">
                                Reset My Password
                            </a>
                        </p>
                        <p>This link expires in 15 minutes and can
                           only be used once.</p>
                        <p>If you did not request this change,
                           you can safely ignore this email.</p>
                        <hr>
                        <small>ResumeAI Account Security</small>
                    </div>
                    """.formatted(safeName, resetLink);

            helper.setText(html, true);

            mailSender.send(message);

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Could not send password reset email.",
                    exception
            );
        }
    }
}
