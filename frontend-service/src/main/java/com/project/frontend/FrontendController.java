package com.project.frontend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RefreshScope
@Controller
@SessionAttributes("name")
public class FrontendController {

	// We need the ApplicationContext in order to create a new Resource.
	@Autowired
	private ApplicationContext context;

	// We need to know the Project ID, because it's Cloud Storage bucket name
	@Autowired
	private GcpProjectIdProvider projectIdProvider;

	@Autowired
	private GuestbookMessagesClient guestbookMessagesClient;
	
	@Value("${greeting:Hello}")
	private String greeting;
	
	@GetMapping("/")
	public String index(Model model) {
		if (model.containsAttribute("name")) {
			String name = (String) model.asMap().get("name");
			model.addAttribute("greeting", String.format("%s %s", greeting, name));
		}
		model.addAttribute("messages", guestbookMessagesClient.getMessages().getContent());
		return "index";
	}
	
	@PostMapping("/post")
	public String post(@RequestParam(name="file", required=false) MultipartFile file, @RequestParam String name, @RequestParam String message, Model model) throws IOException {

		model.addAttribute("name", name);

		String filename = null;
		if (file != null && !file.isEmpty()
				&& file.getContentType().equals("image/jpeg")) {

			// Bucket ID is our Project ID
			String bucket = "gs://" + projectIdProvider.getProjectId() + ".appspot.com";
			// Generate a random file name
			filename = UUID.randomUUID().toString() + ".jpg";
			WritableResource resource = (WritableResource)
					context.getResource(bucket + "/" + filename);

			// Write the file to Cloud Storage using WritableResource
			try (OutputStream os = resource.getOutputStream()) {
				os.write(file.getBytes());
			}
		}

		if (message != null && !message.trim().isEmpty()) {
			// Post the message to the backend service
			Map<String, String> payload = new HashMap<>();
			payload.put("name", name);
			payload.put("message", message);
			// Store the generated file name in the database
			payload.put("imageUri", filename);
			guestbookMessagesClient.add(payload);
		}
		return "redirect:/";
  }

	// ".+" is necessary to capture URI with filename extension
	@GetMapping("/image/{filename:.+}")
	public ResponseEntity<Resource> file(@PathVariable String filename) {
		// Bucket ID is our Project ID

		// Bucket ID is our Project ID
		String bucket = "gs://" + projectIdProvider.getProjectId() + ".appspot.com";
		// Use "gs://" URI to construct a Spring Resource object
		Resource image = context.getResource(bucket + "/" + filename);

		// Send it back to the client
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_JPEG);
		return new ResponseEntity<>(image, headers, HttpStatus.OK);
	}

}

