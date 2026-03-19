package com.tutor.app.ame.controller;

import com.tutor.app.ame.model.AcademicLevel;
import com.tutor.app.ame.model.AppUser;
import com.tutor.app.ame.model.AssignmentCategory;
import com.tutor.app.ame.model.AssignmentSubmission;
import com.tutor.app.ame.service.AssignmentFlowService;
import com.tutor.app.ame.service.CountryCatalogService;
import com.tutor.app.ame.service.OtpEmailService;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Controller
public class AmeController {

    private static final String SESSION_USER_EMAIL = "ame_user_email";
    private static final String SESSION_USER_NAME = "ame_user_name";
    private static final String SESSION_ATTEMPTS = "ame_attempt_map";
    private static final String SESSION_QUOTE = "ame_current_quote";
    private static final String SESSION_INPUT = "ame_current_input";
    private static final String SESSION_PENDING_SIGNUP = "ame_pending_signup";
    private static final String SESSION_UPLOAD_META = "ame_upload_meta";

    private final UserAccountService userAccountService;
    private final AssignmentFlowService assignmentFlowService;
    private final CountryCatalogService countryCatalogService;
    private final OtpEmailService otpEmailService;

    public AmeController(
            UserAccountService userAccountService,
            AssignmentFlowService assignmentFlowService,
            CountryCatalogService countryCatalogService,
            OtpEmailService otpEmailService
    ) {
        this.userAccountService = userAccountService;
        this.assignmentFlowService = assignmentFlowService;
        this.countryCatalogService = countryCatalogService;
        this.otpEmailService = otpEmailService;
    }

    @GetMapping("/")
    public String welcome(Model model, HttpSession session) {
        addSharedModel(model, session);
        return "ame/welcome";
    }

    @GetMapping("/how-it-works")
    public String howItWorks(Model model, HttpSession session) {
        addSharedModel(model, session);
        return "ame/how-it-works";
    }

    @GetMapping("/auth")
    public String authPage(Model model, HttpSession session) {
        addSharedModel(model, session);
        return "ame/auth";
    }

    @GetMapping("/upload")
    public String uploadStart(Model model, HttpSession session) {
        addSharedModel(model, session);
        if (!isLoggedIn(session)) {
            return "redirect:/auth";
        }
        model.addAttribute("draftId", UUID.randomUUID().toString());
        model.addAttribute("categories", AssignmentCategory.values());
        model.addAttribute("levels", AcademicLevel.values());
        model.addAttribute("countries", countryCatalogService.all().values());
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
            userAccountService.validateSignupInput(displayName, email, password);
            String otp = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
            PendingSignup pendingSignup = new PendingSignup(
                    displayName.trim(),
                    email.trim().toLowerCase(),
                    password,
                    otp,
                    Instant.now().plus(10, ChronoUnit.MINUTES)
            );
            otpEmailService.sendOtp(pendingSignup.email(), otp);
            session.setAttribute(SESSION_PENDING_SIGNUP, pendingSignup);
            redirectAttributes.addFlashAttribute("success", "A one-time passcode has been sent to your email.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/auth";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/auth";
        }
        return "redirect:/auth/verify";
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
        return "redirect:/upload";
    }

    @GetMapping("/auth/verify")
    public String verifyOtpPage(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        PendingSignup pendingSignup = (PendingSignup) session.getAttribute(SESSION_PENDING_SIGNUP);
        if (pendingSignup == null) {
            redirectAttributes.addFlashAttribute("error", "Start sign up first.");
            return "redirect:/auth";
        }
        model.addAttribute("pendingEmail", pendingSignup.email());
        model.addAttribute("expiresAt", pendingSignup.expiresAt());
        return "ame/verify-otp";
    }

    @PostMapping("/auth/verify")
    public String verifyOtp(
            @RequestParam String otp,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        PendingSignup pendingSignup = (PendingSignup) session.getAttribute(SESSION_PENDING_SIGNUP);
        if (pendingSignup == null) {
            redirectAttributes.addFlashAttribute("error", "No pending sign up found.");
            return "redirect:/auth";
        }
        if (Instant.now().isAfter(pendingSignup.expiresAt())) {
            session.removeAttribute(SESSION_PENDING_SIGNUP);
            redirectAttributes.addFlashAttribute("error", "OTP expired. Please sign up again.");
            return "redirect:/auth";
        }
        if (!pendingSignup.otpCode().equals(otp.trim())) {
            redirectAttributes.addFlashAttribute("error", "Invalid OTP.");
            return "redirect:/auth/verify";
        }
        AppUser user = userAccountService.register(
                pendingSignup.displayName(),
                pendingSignup.email(),
                pendingSignup.rawPassword()
        );
        session.removeAttribute(SESSION_PENDING_SIGNUP);
        session.setAttribute(SESSION_USER_EMAIL, user.getEmail());
        session.setAttribute(SESSION_USER_NAME, user.getDisplayName());
        redirectAttributes.addFlashAttribute("success", "Account verified and created.");
        return "redirect:/upload";
    }

    @PostMapping("/auth/resend-otp")
    public String resendOtp(HttpSession session, RedirectAttributes redirectAttributes) {
        PendingSignup pendingSignup = (PendingSignup) session.getAttribute(SESSION_PENDING_SIGNUP);
        if (pendingSignup == null) {
            redirectAttributes.addFlashAttribute("error", "No pending sign up found.");
            return "redirect:/auth";
        }
        String otp = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
        try {
            session.setAttribute(SESSION_PENDING_SIGNUP, new PendingSignup(
                    pendingSignup.displayName(),
                    pendingSignup.email(),
                    pendingSignup.rawPassword(),
                    otp,
                    Instant.now().plus(10, ChronoUnit.MINUTES)
            ));
            otpEmailService.sendOtp(pendingSignup.email(), otp);
            redirectAttributes.addFlashAttribute("success", "New OTP sent.");
            return "redirect:/auth/verify";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/auth/verify";
        }
    }

    @PostMapping("/auth/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(SESSION_USER_EMAIL);
        session.removeAttribute(SESSION_USER_NAME);
        session.removeAttribute(SESSION_PENDING_SIGNUP);
        session.removeAttribute(SESSION_UPLOAD_META);
        return "redirect:/";
    }

    @PostMapping("/upload/start")
    public String saveUploadStart(
            @RequestParam String draftId,
            @RequestParam String country,
            @RequestParam AssignmentCategory category,
            @RequestParam AcademicLevel academicLevel,
            @RequestParam String subject,
            @RequestParam(required = false) String userTimezone,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!isLoggedIn(session)) {
            return "redirect:/auth";
        }
        UploadMeta meta = new UploadMeta(
                draftId,
                country,
                category,
                academicLevel,
                subject.trim(),
                userTimezone
        );
        session.setAttribute(SESSION_UPLOAD_META, meta);
        redirectAttributes.addFlashAttribute("success", "Great. Now add files and instructions.");
        return "redirect:/upload/content";
    }

    @GetMapping("/upload/content")
    public String uploadContent(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/auth";
        }
        UploadMeta meta = (UploadMeta) session.getAttribute(SESSION_UPLOAD_META);
        if (meta == null) {
            redirectAttributes.addFlashAttribute("error", "Start with assignment details first.");
            return "redirect:/upload";
        }
        addSharedModel(model, session);
        model.addAttribute("meta", meta);
        return "ame/upload-content";
    }

    @PostMapping("/analyze")
    public String analyze(
            @RequestParam(defaultValue = "0") int wordCount,
            @RequestParam String prompt,
            @RequestParam(defaultValue = "false") boolean inDepthExplanation,
            @RequestParam("files") List<MultipartFile> files,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!isLoggedIn(session)) {
            redirectAttributes.addFlashAttribute("error", "Please sign up or login first.");
            return "redirect:/auth";
        }
        UploadMeta meta = (UploadMeta) session.getAttribute(SESSION_UPLOAD_META);
        if (meta == null) {
            redirectAttributes.addFlashAttribute("error", "Complete upload step 1 first.");
            return "redirect:/upload";
        }

        int totalBytes = (int) files.stream().mapToLong(MultipartFile::getSize).sum();
        AssignmentFlowService.AssignmentInput input = new AssignmentFlowService.AssignmentInput(
                meta.draftId(),
                meta.country(),
                meta.category(),
                meta.academicLevel(),
                meta.subject(),
                wordCount,
                prompt.trim(),
                inDepthExplanation,
                meta.userTimezone(),
                totalBytes
        );

        try {
            Map<String, Integer> attemptMap = getAttemptMap(session);
            int currentAttempts = attemptMap.getOrDefault(meta.draftId(), 0);
            AssignmentFlowService.QuoteResult quote = assignmentFlowService.analyze(input, files, currentAttempts);
            attemptMap.put(meta.draftId(), quote.attempts());
            session.setAttribute(SESSION_QUOTE, quote);
            session.setAttribute(SESSION_INPUT, input);
            if (quote.categoryMessage() != null) {
                redirectAttributes.addFlashAttribute(quote.autoCorrected() ? "success" : "error", quote.categoryMessage());
                if (!quote.autoCorrected()) {
                    return "redirect:/upload";
                }
            }
            return "redirect:/estimate";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/upload";
        }
    }

    @GetMapping("/estimate")
    public String estimate(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        AssignmentFlowService.QuoteResult quote = (AssignmentFlowService.QuoteResult) session.getAttribute(SESSION_QUOTE);
        AssignmentFlowService.AssignmentInput input = (AssignmentFlowService.AssignmentInput) session.getAttribute(SESSION_INPUT);
        if (quote == null || input == null) {
            redirectAttributes.addFlashAttribute("error", "No assignment analysis found yet.");
            return "redirect:/upload";
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
            return "redirect:/upload";
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
            return "redirect:/auth";
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
            return "redirect:/auth";
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

    private record PendingSignup(
            String displayName,
            String email,
            String rawPassword,
            String otpCode,
            Instant expiresAt
    ) {
    }

    private record UploadMeta(
            String draftId,
            String country,
            AssignmentCategory category,
            AcademicLevel academicLevel,
            String subject,
            String userTimezone
    ) {
    }
}
