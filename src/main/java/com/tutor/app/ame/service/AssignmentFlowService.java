package com.tutor.app.ame.service;

import com.tutor.app.ame.model.AcademicLevel;
import com.tutor.app.ame.model.AssignmentCategory;
import com.tutor.app.ame.model.AssignmentStatus;
import com.tutor.app.ame.model.AssignmentSubmission;
import com.tutor.app.ame.repository.AssignmentSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AssignmentFlowService {

    private static final BigDecimal BASE_PRICE = new BigDecimal("200");
    private static final List<Integer> DELIVERY_OPTIONS_HOURS = List.of(
            2, 6, 12, 18, 24, 30, 36, 42, 48, 54, 60, 66, 72, 96, 120, 144, 168
    );

    private final AssignmentSubmissionRepository submissionRepository;
    private final CountryCatalogService countryCatalogService;
    private final CurrencyRateService currencyRateService;

    public AssignmentFlowService(
            AssignmentSubmissionRepository submissionRepository,
            CountryCatalogService countryCatalogService,
            CurrencyRateService currencyRateService
    ) {
        this.submissionRepository = submissionRepository;
        this.countryCatalogService = countryCatalogService;
        this.currencyRateService = currencyRateService;
    }

    public QuoteResult analyze(
            AssignmentInput input,
            List<MultipartFile> files,
            int currentCategoryAttempts
    ) {
        CountryCatalogService.CountryProfile country = countryCatalogService.get(input.country())
                .orElseThrow(() -> new IllegalArgumentException("This country is not currently supported."));
        validateFileBundle(files);

        AssignmentCategory detected = detectCategory(input.subject(), input.prompt());
        boolean mismatch = detected != input.selectedCategory();
        int attempts = mismatch ? currentCategoryAttempts + 1 : currentCategoryAttempts;

        AssignmentCategory finalCategory = input.selectedCategory();
        String categoryMessage = null;
        boolean autoCorrected = false;
        if (mismatch) {
            if (attempts >= 2) {
                finalCategory = detected;
                autoCorrected = true;
                categoryMessage = "We auto-corrected your category to " + detected + " for this assignment.";
            } else {
                categoryMessage = "This appears to be " + detected + ". Please review your selected category.";
            }
        }

        BigDecimal readability = readabilityScore(files);
        if (readability.compareTo(new BigDecimal("0.80")) < 0) {
            throw new IllegalArgumentException("The upload is below 80% readability. Please re-upload clearer files.");
        }

        BigDecimal difficultyIndex = calculateDifficultyIndex(input, files, finalCategory);
        BigDecimal reevaluated = difficultyIndex.multiply(new BigDecimal("4"))
                .setScale(2, RoundingMode.HALF_UP);
        int predictedHours = calculatePredictedHours(reevaluated);

        return new QuoteResult(
                country,
                finalCategory,
                detected,
                attempts,
                autoCorrected,
                categoryMessage,
                readability,
                difficultyIndex,
                reevaluated,
                predictedHours
        );
    }

    public AssignmentSubmission createSubmission(
            AssignmentInput input,
            QuoteResult quote,
            Integer userRequiredHours,
            String userEmail,
            String userName
    ) {
        int requiredHours = userRequiredHours == null ? quote.predictedHours() : userRequiredHours;
        if (!DELIVERY_OPTIONS_HOURS.contains(requiredHours)) {
            throw new IllegalArgumentException("Pick a valid delivery option.");
        }

        BigDecimal urgencyConstant = BigDecimal.ONE;
        if (requiredHours != quote.predictedHours()) {
            urgencyConstant = new BigDecimal("0.5")
                    .multiply(BigDecimal.valueOf(quote.predictedHours()))
                    .divide(BigDecimal.valueOf(requiredHours), 2, RoundingMode.HALF_UP);
        }
        if (urgencyConstant.compareTo(new BigDecimal("30.5")) > 0 || urgencyConstant.compareTo(new BigDecimal("0.4")) < 0) {
            throw new IllegalArgumentException("Not possible: urgency constant is outside allowed range (0.4 to 30.5).");
        }

        BigDecimal cec = cecMultiplier(input.wordCount(), quote.finalCategory());
        BigDecimal level = levelMultiplier(input.academicLevel());
        BigDecimal totalUsd = BASE_PRICE
                .multiply(cec)
                .multiply(level)
                .multiply(quote.difficultyIndex())
                .multiply(urgencyConstant);
        if (quote.finalCategory() == AssignmentCategory.COLLEGE_APPLICATION) {
            totalUsd = totalUsd.multiply(new BigDecimal("2.0"));
        }
        if (input.inDepthExplanation()) {
            totalUsd = totalUsd.multiply(new BigDecimal("1.5"));
        }
        totalUsd = totalUsd.setScale(2, RoundingMode.HALF_UP);

        BigDecimal fxRate = currencyRateService.usdTo(quote.country().currencyCode());
        BigDecimal localAmount = totalUsd.multiply(fxRate).setScale(2, RoundingMode.HALF_UP);

        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setDraftId(input.draftId());
        submission.setUserEmail(userEmail);
        submission.setUserName(userName);
        submission.setCountry(quote.country().name());
        submission.setCurrencyCode(quote.country().currencyCode());
        submission.setSelectedCategory(input.selectedCategory());
        submission.setDetectedCategory(quote.detectedCategory());
        submission.setAcademicLevel(input.academicLevel());
        submission.setSubject(input.subject());
        submission.setPrompt(input.prompt());
        submission.setWordCount(input.wordCount());
        submission.setInDepthExplanation(input.inDepthExplanation());
        submission.setBundleBytes(input.bundleBytes());
        submission.setReadabilityScore(quote.readabilityScore());
        submission.setAiDifficultyIndex(quote.difficultyIndex());
        submission.setAiDifficultyIndexReevaluated(quote.reevaluatedDifficultyIndex());
        submission.setPredictedHours(quote.predictedHours());
        submission.setRequiredHours(requiredHours);
        submission.setUrgencyConstant(urgencyConstant);
        submission.setTotalUsd(totalUsd);
        submission.setUsdToLocalRate(fxRate);
        submission.setTotalLocal(localAmount);
        submission.setCreatedAt(Instant.now());
        submission.setExpectedDeliveryUtc(Instant.now().plusSeconds(requiredHours * 3600L));
        submission.setUserTimezone(input.userTimezone());
        submission.setStatus(AssignmentStatus.AWAITING_PAYMENT);
        return submissionRepository.save(submission);
    }

    public AssignmentSubmission markPaidAndStart(Long id) {
        AssignmentSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found."));
        submission.setStatus(AssignmentStatus.IN_PROGRESS);
        submission.setDeliveredContent(generateGuidedOutput(submission));
        return submissionRepository.save(submission);
    }

    public void refreshDeliveries() {
        List<AssignmentSubmission> inProgress = submissionRepository.findByStatusOrderByExpectedDeliveryUtcAsc(AssignmentStatus.IN_PROGRESS);
        Instant now = Instant.now();
        for (AssignmentSubmission s : inProgress) {
            if (!now.isBefore(s.getExpectedDeliveryUtc())) {
                s.setStatus(AssignmentStatus.DELIVERED);
                s.setDeliveredAtUtc(now);
                submissionRepository.save(s);
            }
        }
    }

    public List<AssignmentSubmission> historyFor(String email) {
        refreshDeliveries();
        if (email == null || email.isBlank()) {
            return submissionRepository.findAll().stream()
                    .sorted(Comparator.comparing(AssignmentSubmission::getCreatedAt).reversed())
                    .toList();
        }
        return submissionRepository.findByUserEmailIgnoreCaseOrderByCreatedAtDesc(email);
    }

    public void saveReview(Long id, int rating, String review) {
        AssignmentSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found."));
        submission.setRating(Math.max(1, Math.min(5, rating)));
        submission.setReview(review == null ? null : review.trim());
        submissionRepository.save(submission);
    }

    public AssignmentSubmission getOne(Long id) {
        refreshDeliveries();
        return submissionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Submission not found."));
    }

    public Map<Integer, String> deliveryOptions() {
        Map<Integer, String> options = new LinkedHashMap<>();
        for (Integer hours : DELIVERY_OPTIONS_HOURS) {
            if (hours <= 72) {
                options.put(hours, hours + " hours");
            } else {
                options.put(hours, (hours / 24) + " days (" + hours + " hours)");
            }
        }
        return options;
    }

    public String formatUserTime(AssignmentSubmission submission) {
        String timezone = submission.getUserTimezone() == null || submission.getUserTimezone().isBlank()
                ? "UTC"
                : submission.getUserTimezone();
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime at = submission.getExpectedDeliveryUtc().atZone(zone);
        return at.toLocalDate() + " " + at.toLocalTime().withNano(0) + " (" + timezone + ")";
    }

    public int uploadsUntilUnlimited(String email) {
        int threshold = 20;
        int total = (int) historyFor(email).size();
        return Math.max(0, threshold - total);
    }

    private AssignmentCategory detectCategory(String subject, String prompt) {
        String text = (subject + " " + prompt).toLowerCase(Locale.ROOT);
        if (containsAny(text, "college", "application", "statement of purpose", "sop")) {
            return AssignmentCategory.COLLEGE_APPLICATION;
        }
        if (containsAny(text, "thesis", "dissertation")) {
            return AssignmentCategory.THESIS;
        }
        if (containsAny(text, "essay", "letter", "story", "writing", "language")) {
            return AssignmentCategory.WRITING;
        }
        if (containsAny(text, "research", "review", "literature")) {
            return AssignmentCategory.RESEARCH;
        }
        if (containsAny(text, "project", "capstone", "prototype")) {
            return AssignmentCategory.PROJECT;
        }
        return AssignmentCategory.STEM;
    }

    private BigDecimal readabilityScore(List<MultipartFile> files) {
        BigDecimal total = BigDecimal.ZERO;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            boolean accepted = name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".ppt")
                    || name.endsWith(".pptx") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".png") || name.endsWith(".txt");
            if (!accepted) {
                return BigDecimal.ZERO;
            }

            double score = 0.0;
            score += 0.4;
            score += file.getSize() >= 300 ? 0.2 : 0.0;

            try {
                byte[] bytes = file.getBytes();
                int sample = Math.min(bytes.length, 2048);
                int printable = 0;
                for (int i = 0; i < sample; i++) {
                    int b = bytes[i] & 0xFF;
                    if ((b >= 32 && b <= 126) || b == 9 || b == 10 || b == 13) {
                        printable++;
                    }
                }
                double ratio = sample == 0 ? 0 : ((double) printable / sample);
                score += Math.min(0.4, ratio * 0.4);
            } catch (IOException ignored) {
            }
            total = total.add(BigDecimal.valueOf(Math.min(1.0, score)));
        }
        return total.divide(BigDecimal.valueOf(files.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDifficultyIndex(AssignmentInput input, List<MultipartFile> files, AssignmentCategory finalCategory) {
        double bundleMb = input.bundleBytes() / (1024.0 * 1024.0);
        double score = 1.0 + (bundleMb / 5.0) * 0.8;
        score += switch (input.academicLevel()) {
            case HIGH_SCHOOL -> 0.1;
            case UNIVERSITY -> 0.4;
            case POSTGRADUATE -> 0.8;
        };
        if (isWritingLike(finalCategory)) {
            score += input.wordCount() <= 500 ? 0.2 : input.wordCount() <= 1500 ? 0.5 : 0.9;
        }
        if (input.inDepthExplanation()) {
            score += 0.4;
        }
        score = Math.max(1.0, Math.min(3.0, score));
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private int calculatePredictedHours(BigDecimal reevaluatedDifficulty) {
        double i = Math.max(1.0, Math.min(12.0, reevaluatedDifficulty.doubleValue()));
        double normalized = (i - 1.0) / 11.0;
        double rawHours = 2.0 + (normalized * 166.0);
        int clamped = (int) Math.round(Math.max(2.0, Math.min(168.0, rawHours)));
        if (clamped <= 2) {
            return 2;
        }
        int rounded = (int) Math.round(clamped / 6.0) * 6;
        return Math.max(6, Math.min(168, rounded));
    }

    private BigDecimal cecMultiplier(int wordCount, AssignmentCategory category) {
        if (!isWritingLike(category)) {
            return BigDecimal.ONE;
        }
        if (wordCount <= 250) {
            return new BigDecimal("1.6");
        }
        if (wordCount <= 500) {
            return new BigDecimal("1.8");
        }
        if (wordCount <= 800) {
            return new BigDecimal("2.0");
        }
        if (wordCount <= 1500) {
            return new BigDecimal("2.5");
        }
        return new BigDecimal("3.5");
    }

    private BigDecimal levelMultiplier(AcademicLevel level) {
        return switch (level) {
            case HIGH_SCHOOL -> new BigDecimal("1.1");
            case UNIVERSITY -> new BigDecimal("1.5");
            case POSTGRADUATE -> new BigDecimal("3.0");
        };
    }

    private boolean isWritingLike(AssignmentCategory category) {
        return category == AssignmentCategory.WRITING
                || category == AssignmentCategory.RESEARCH
                || category == AssignmentCategory.THESIS
                || category == AssignmentCategory.COLLEGE_APPLICATION;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void validateFileBundle(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Upload at least one file.");
        }
        long total = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("Bundle upload exceeds 5MB.");
        }
    }

    private String generateGuidedOutput(AssignmentSubmission submission) {
        StringBuilder sb = new StringBuilder();
        sb.append("AME Guided Solution\n");
        sb.append("Assignment ID: ").append(submission.getId()).append("\n");
        sb.append("Subject: ").append(submission.getSubject()).append("\n");
        sb.append("Category: ").append(submission.getDetectedCategory()).append("\n\n");
        sb.append("1) Structured worked solution with clear steps.\n");
        sb.append("2) Feedback section showing areas to improve before submission.\n");
        sb.append("3) Editing notes and clarity upgrades for your draft.\n");
        if (submission.isInDepthExplanation()) {
            sb.append("\nFirst-Principles + Feynman-style explanation is included at the end.\n");
        }
        sb.append("\nEthical use notice: Adapt this content to your own voice and institutional policy.");
        return sb.toString();
    }

    public record AssignmentInput(
            String draftId,
            String country,
            AssignmentCategory selectedCategory,
            AcademicLevel academicLevel,
            String subject,
            int wordCount,
            String prompt,
            boolean inDepthExplanation,
            String userTimezone,
            int bundleBytes
    ) {
    }

    public record QuoteResult(
            CountryCatalogService.CountryProfile country,
            AssignmentCategory finalCategory,
            AssignmentCategory detectedCategory,
            int attempts,
            boolean autoCorrected,
            String categoryMessage,
            BigDecimal readabilityScore,
            BigDecimal difficultyIndex,
            BigDecimal reevaluatedDifficultyIndex,
            int predictedHours
    ) {
    }

    public List<AssignmentSubmission> deliveredOnly(String email) {
        List<AssignmentSubmission> list = new ArrayList<>();
        for (AssignmentSubmission submission : historyFor(email)) {
            if (submission.getStatus() == AssignmentStatus.DELIVERED) {
                list.add(submission);
            }
        }
        return list;
    }
}
