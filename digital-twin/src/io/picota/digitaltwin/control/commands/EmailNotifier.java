package io.picota.digitaltwin.control.commands;

import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.model.DigitalTwin;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class EmailNotifier {
	private final DigitalTwin digitalTwin;
	private final Properties mailProps;
	private String username;
	private String password;
	private String from;

	public EmailNotifier(DigitalTwin digitalTwin, File confFile) {
		this.digitalTwin = digitalTwin;
		this.mailProps = new Properties();
		try (FileInputStream fis = new FileInputStream(confFile)) {
			mailProps.load(fis);
			this.username = mailProps.getProperty("mail.username");
			this.password = mailProps.getProperty("mail.password");
			this.from = mailProps.getProperty("mail.from");
		} catch (IOException ignored) {
		}
	}

	public void notifyFailedExecution() {
		if (this.username == null) return;
		String subject = "Digital Twin - Execution Failure";
		String body = "An error occurred during the execution of the Digital Twin.";
		send(subject, body, digitalTwin.archetype().reportFile());
	}

	public void notifyExecution(DigitalTwin.TrainingReport report) {
		if (this.username == null) return;
		String subject = "Digital Twin - Execution Completed";
		String body = "The execution of the Digital Twin has completed successfully.";
		send(subject, body, digitalTwin.archetype().reportFile());
	}

	private void send(String subject, String body, File attachment) {
		Session session = Session.getInstance(mailProps, authenticator());
		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(from));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(digitalTwin.notifyEmail()));
			message.setSubject(subject);
			MimeBodyPart textPart = new MimeBodyPart();
			textPart.setText(body);
			MimeBodyPart attachmentPart = new MimeBodyPart();
			attachmentPart.attachFile(attachment);
			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(textPart);
			multipart.addBodyPart(attachmentPart);
			message.setContent(multipart);
			Transport.send(message);
		} catch (MessagingException | IOException e) {
			Logger.error("Failed to send email: " + subject);
		}
	}

	private Authenticator authenticator() {
		return new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		};
	}
}
