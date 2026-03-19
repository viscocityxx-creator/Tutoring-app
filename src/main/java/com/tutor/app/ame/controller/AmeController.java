package com.tutor.app.ame.controller;

import com.tutor.app.ame.model.AcademicLevel;
import com.tutor.app.ame.model.AppUser;
import com.tutor.app.ame.model.AssignmentCategory;
import com.tutor.app.ame.model.AssignmentSubmission;
import com.tutor.app.ame.service.AssignmentFlowService;
import com.tutor.app.ame.service.CountryCatalogService;
import com.tutor.app.ame.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class AmeController {

    private static final String SESSION_USER_EMAIL = "ame_user_email";
    private static final String SESSION_USER_NAME = "ame_user_name";
    private static final String SESSION_ATTEMPTS = "ame_attempt_map";
    private static final String SESSION_QUOTE = "ame_current_quote";
    private static final String SESSION_INPUT = "ame_current_input";

    private final UserAccountService userAccountService;
    private final AssignmentFlowService assignmentFlowService;
    private final CountryCatalogService countryCatalogService;

    public AmeController(
            UserAccountService userAccountService,
            AssignmentFlowService assignmentFlowService,
            CountryCatalogService countryCatalogService
    ) {
        this.userAccountService = userAccountService;
        this.assignmentFlowService = assignmentFlowService;
        this.countryCatalogService = countryCatalogService;
    }

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        addSharedModel(model, session);
        model.addAttribute("draftId", UUID.randomUUID().toString());
        model.addAttribute("categories", AssignmentCategory.values());
        model.addAttribute("levels", AcademicLevel.values());
        model.addAttribute("countries", countryCatalogService.all().values());
        model.addAttribute("deliveryOptions", assignmentFlowService.deliveryOptions());
        return "ame/index";
    }

    @PostMapping("/auth/signup")
    public String signup(
            @RequestParam String displayName,
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            AppUser user = userAccountService.register(displayName, email, password);
            session.setAttribute(SESSION_USER_EMAIL, user.getEmail());
            session.setAttribute(SESSION_USER_NAME, user.getDisplayName());
            redirectAttributes.addFlashAttribute("success", "Account created. You're logged in.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/auth/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        userAccountService.login(email, password)
                .ifPresentOrElse(user -> {
                    session.setAttribute(SESSION_USER_EMAIL, user.getEmail());
                    session.setAttribute(SESSION_USER_NAME, user.getDisplayName());
                }, () -> redirectAttributes.addFlashAttribute("error", "Invalid login details."));
        return "redirect:/";
    }

    @PostMapping("/auth/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(SESSION_USER_EMAIL);
        session.removeAttribute(SESSION_USER_NAME);
        return "redirect:/";
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam String draftId,
            @RequestParam String country,
            @RequestParam AssignmentCategory category,
            @RequestParam AcademicLevel academicLevel,
            @RequestParam String subject,
            @RequestParam(defaultValue = "0") int wordCount,
            @RequestParam String prompt,
            @RequestParam(defaultValue = "false") boolean inDepthExplanation,
            @RequestParam(required = false) String userTimezone,
            @RequestParam("files") List<MultipartFile> files,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!isLoggedIn(session)) {
            redirectAttributes.addFlashAttribute("error", "Please sign up or login first.");
            return "redirect:/";
        }

        int totalBytes = (int) files.stream().mapToLong(MultipartFile::getSize).sum();
        AssignmentFlowService.AssignmentInput input = new AssignmentFlowService.AssignmentInput(
                draftId,
                country,
                category,
                academicLevel,
                subject.trim(),
                wordCount,
                prompt.trim(),
                inDepthExplanation,
                userTimezone,
                totalBytes
        );

        try {
            Map<String, Integer> attemptMap = getAttemptMap(session);
            int currentAttempts = attemptMap.getOrDefault(draftId, 0);
            AssignmentFlowService.QuoteResult quote = assignmentFlowService.analyze(input, files, currentAttempts);
            attemptMap.put(draftId, quote.attempts());
            session.setAttribute(SESSION_QUOTE, quote);
            session.setAttribute(SESSION_INPUT, input);
            if (quote.categoryMessage() != null) {
                redirectAttributes.addFlashAttribute(quote.autoCorrected() ? "success" : "error", quote.categoryMessage());
                if (!quote.autoCorrected()) {
                    return "redirect:/";
                }
            }
            return "redirect:/estimate";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/estimate")
    public String estimate(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        AssignmentFlowService.QuoteResult quote = (AssignmentFlowService.QuoteResult) session.getAttribute(SESSION_QUOTE);
        AssignmentFlowService.AssignmentInput input = (AssignmentFlowService.AssignmentInput) session.getAttribute(SESSION_INPUT);
        if (quote == null || input == null) {
            redirectAttributes.addFlashAttribute("error", "No assignment analysis found yet.");
            return "redirect:/";
        }
        addSharedModel(model, session);
        model.addAttribute("quote", quote);
        model.addAttribute("input", input);
        model.addAttribute("deliveryOptions", assignmentFlowService.deliveryOptions());
        return "ame/estimate";
    }

    @PostMapping("/quote/confirm")
    public String confirmQuote(
            @RequestParam(required = false) Integer requestedHours,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        AssignmentFlowService.QuoteResult quote = (AssignmentFlowService.QuoteResult) session.getAttribute(SESSION_QUOTE);
        AssignmentFlowService.AssignmentInput input = (AssignmentFlowService.AssignmentInput) session.getAttribute(SESSION_INPUT);
        if (quote == null || input == null) {
            redirectAttributes.addFlashAttribute("error", "Please analyze an assignment first.");
            return "redirect:/";
        }

        try {
            AssignmentSubmission submission = assignmentFlowService.createSubmission(
                    input,
                    quote,
                    requestedHours,
                    (String) session.getAttribute(SESSION_USER_EMAIL),
                    (String) session.getAttribute(SESSION_USER_NAME)
            );
            session.removeAttribute(SESSION_QUOTE);
            session.removeAttribute(SESSION_INPUT);
            return "redirect:/payment/" + submission.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/estimate";
        }
    }

    @GetMapping("/payment/{id}")
    public String paymentPage(@PathVariable Long id, Model model, HttpSession session) {
        addSharedModel(model, session);
        model.addAttribute("submission", assignmentFlowService.getOne(id));
        return "ame/payment";
    }

    @PostMapping("/payment/{id}/complete")
    public String completePayment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        assignmentFlowService.markPaidAndStart(id);
        redirectAttributes.addFlashAttribute("success", "Payment successful. Your guided solution is now in progress.");
        return "redirect:/history";
    }

    @GetMapping("/history")
    public String history(Model model, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        addSharedModel(model, session);
        String email = (String) session.getAttribute(SESSION_USER_EMAIL);
        List<AssignmentSubmission> history = assignmentFlowService.historyFor(email);
        model.addAttribute("history", history);
        model.addAttribute("recent", assignmentFlowService.deliveredOnly(email));
        model.addAttribute("remainingForUnlimited", assignmentFlowService.uploadsUntilUnlimited(email));
        model.addAttribute("timeFormatter", assignmentFlowService);
        return "ame/history";
    }

    @PostMapping("/review/{id}")
    public String saveReview(
            @PathVariable Long id,
            @RequestParam int rating,
            @RequestParam(required = false) String review,
            RedirectAttributes redirectAttributes
    ) {
        assignmentFlowService.saveReview(id, rating, review);
        redirectAttributes.addFlashAttribute("success", "Thanks for your feedback.");
        return "redirect:/history";
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long id) {
        AssignmentSubmission submission = assignmentFlowService.getOne(id);
        String body = submission.getDeliveredContent() == null
                ? "Your work is being prepared."
                : submission.getDeliveredContent();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ame-assignment-" + id + ".txt")
                .contentLength(bytes.length)
                .body(resource);
    }

    @GetMapping("/discourse")
    public String discourse(Model model, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        addSharedModel(model, session);
        String email = (String) session.getAttribute(SESSION_USER_EMAIL);
        List<AssignmentSubmission> reviewed = assignmentFlowService.historyFor(email).stream()
                .filter(s -> s.getReview() != null && !s.getReview().isBlank())
                .toList();
        model.addAttribute("reviewed", reviewed);
        return "ame/discourse";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> getAttemptMap(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ATTEMPTS);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Integer>) map;
        }
        Map<String, Integer> created = new HashMap<>();
        session.setAttribute(SESSION_ATTEMPTS, created);
        return created;
    }

    private void addSharedModel(Model model, HttpSession session) {
        model.addAttribute("loggedIn", isLoggedIn(session));
        model.addAttribute("userName", session.getAttribute(SESSION_USER_NAME));
        model.addAttribute("userEmail", session.getAttribute(SESSION_USER_EMAIL));
    }

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute(SESSION_USER_EMAIL) != null;
    }
}
