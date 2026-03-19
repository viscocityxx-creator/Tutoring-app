package com.tutor.app.ame.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ame_submissions")
public class AssignmentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String draftId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentCategory selectedCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentCategory detectedCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcademicLevel academicLevel;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private int wordCount;

    @Column(nullable = false, length = 3000)
    private String prompt;

    @Column(nullable = false)
    private boolean inDepthExplanation;

    @Column(nullable = false)
    private int bundleBytes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal readabilityScore;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal aiDifficultyIndex;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal aiDifficultyIndexReevaluated;

    @Column(nullable = false)
    private int predictedHours;

    @Column(nullable = false)
    private int requiredHours;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal urgencyConstant;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalUsd;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalNgn;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLocal;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal usdToLocalRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expectedDeliveryUtc;

    private String userTimezone;

    @Lob
    private String deliveredContent;

    private Instant deliveredAtUtc;

    private Integer rating;

    @Column(length = 2000)
    private String review;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDraftId() {
        return draftId;
    }

    public void setDraftId(String draftId) {
        this.draftId = draftId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public AssignmentCategory getSelectedCategory() {
        return selectedCategory;
    }

    public void setSelectedCategory(AssignmentCategory selectedCategory) {
        this.selectedCategory = selectedCategory;
    }

    public AssignmentCategory getDetectedCategory() {
        return detectedCategory;
    }

    public void setDetectedCategory(AssignmentCategory detectedCategory) {
        this.detectedCategory = detectedCategory;
    }

    public AcademicLevel getAcademicLevel() {
        return academicLevel;
    }

    public void setAcademicLevel(AcademicLevel academicLevel) {
        this.academicLevel = academicLevel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean isInDepthExplanation() {
        return inDepthExplanation;
    }

    public void setInDepthExplanation(boolean inDepthExplanation) {
        this.inDepthExplanation = inDepthExplanation;
    }

    public int getBundleBytes() {
        return bundleBytes;
    }

    public void setBundleBytes(int bundleBytes) {
        this.bundleBytes = bundleBytes;
    }

    public BigDecimal getReadabilityScore() {
        return readabilityScore;
    }

    public void setReadabilityScore(BigDecimal readabilityScore) {
        this.readabilityScore = readabilityScore;
    }

    public BigDecimal getAiDifficultyIndex() {
        return aiDifficultyIndex;
    }

    public void setAiDifficultyIndex(BigDecimal aiDifficultyIndex) {
        this.aiDifficultyIndex = aiDifficultyIndex;
    }

    public BigDecimal getAiDifficultyIndexReevaluated() {
        return aiDifficultyIndexReevaluated;
    }

    public void setAiDifficultyIndexReevaluated(BigDecimal aiDifficultyIndexReevaluated) {
        this.aiDifficultyIndexReevaluated = aiDifficultyIndexReevaluated;
    }

    public int getPredictedHours() {
        return predictedHours;
    }

    public void setPredictedHours(int predictedHours) {
        this.predictedHours = predictedHours;
    }

    public int getRequiredHours() {
        return requiredHours;
    }

    public void setRequiredHours(int requiredHours) {
        this.requiredHours = requiredHours;
    }

    public BigDecimal getUrgencyConstant() {
        return urgencyConstant;
    }

    public void setUrgencyConstant(BigDecimal urgencyConstant) {
        this.urgencyConstant = urgencyConstant;
    }

    public BigDecimal getTotalUsd() {
        return totalUsd;
    }

    public void setTotalUsd(BigDecimal totalUsd) {
        this.totalUsd = totalUsd;
    }

    public BigDecimal getTotalNgn() {
        return totalNgn;
    }

    public void setTotalNgn(BigDecimal totalNgn) {
        this.totalNgn = totalNgn;
    }

    public BigDecimal getTotalLocal() {
        return totalLocal;
    }

    public void setTotalLocal(BigDecimal totalLocal) {
        this.totalLocal = totalLocal;
    }

    public BigDecimal getUsdToLocalRate() {
        return usdToLocalRate;
    }

    public void setUsdToLocalRate(BigDecimal usdToLocalRate) {
        this.usdToLocalRate = usdToLocalRate;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpectedDeliveryUtc() {
        return expectedDeliveryUtc;
    }

    public void setExpectedDeliveryUtc(Instant expectedDeliveryUtc) {
        this.expectedDeliveryUtc = expectedDeliveryUtc;
    }

    public String getUserTimezone() {
        return userTimezone;
    }

    public void setUserTimezone(String userTimezone) {
        this.userTimezone = userTimezone;
    }

    public String getDeliveredContent() {
        return deliveredContent;
    }

    public void setDeliveredContent(String deliveredContent) {
        this.deliveredContent = deliveredContent;
    }

    public Instant getDeliveredAtUtc() {
        return deliveredAtUtc;
    }

    public void setDeliveredAtUtc(Instant deliveredAtUtc) {
        this.deliveredAtUtc = deliveredAtUtc;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }
}
