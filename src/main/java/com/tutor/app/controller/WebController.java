package com.tutor.app.controller;

import com.tutor.app.model.SessionStatus;
import com.tutor.app.model.Student;
import com.tutor.app.model.Subject;
import com.tutor.app.model.Tutor;
import com.tutor.app.model.TutoringSession;
import com.tutor.app.repository.StudentRepository;
import com.tutor.app.repository.TutorRepository;
import com.tutor.app.repository.TutoringSessionRepository;
import com.tutor.app.service.BookingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/legacy")
public class WebController {

    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final TutoringSessionRepository sessionRepository;
    private final BookingService bookingService;

    public WebController(
            TutorRepository tutorRepository,
            StudentRepository studentRepository,
            TutoringSessionRepository sessionRepository,
            BookingService bookingService
    ) {
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.bookingService = bookingService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tutorCount", tutorRepository.count());
        model.addAttribute("studentCount", studentRepository.count());
        model.addAttribute("scheduledCount", sessionRepository.findByStatus(SessionStatus.SCHEDULED).size());
        return "index";
    }

    @GetMapping("/tutors")
    public String tutors(Model model) {
        model.addAttribute("tutors", tutorRepository.findAll());
        model.addAttribute("subjects", Subject.values());
        return "tutors";
    }

    @PostMapping("/tutors")
    public String createTutor(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String bio,
            @RequestParam BigDecimal hourlyRate,
            @RequestParam String subjectsCsv,
            RedirectAttributes redirectAttributes
    ) {
        Set<Subject> subjects = parseSubjects(subjectsCsv);
        if (subjects.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Add at least one valid subject.");
            return "redirect:/tutors";
        }

        Tutor tutor = new Tutor();
        tutor.setName(name);
        tutor.setEmail(email);
        tutor.setBio(bio);
        tutor.setHourlyRate(hourlyRate);
        tutor.setSubjects(subjects);
        tutorRepository.save(tutor);
        redirectAttributes.addFlashAttribute("success", "Tutor created.");
        return "redirect:/tutors";
    }

    @GetMapping("/students")
    public String students(Model model) {
        model.addAttribute("students", studentRepository.findAll());
        return "students";
    }

    @PostMapping("/students")
    public String createStudent(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String goal,
            RedirectAttributes redirectAttributes
    ) {
        Student student = new Student();
        student.setName(name);
        student.setEmail(email);
        student.setGoal(goal);
        studentRepository.save(student);
        redirectAttributes.addFlashAttribute("success", "Student created.");
        return "redirect:/students";
    }

    @GetMapping("/sessions")
    public String sessions(Model model) {
        model.addAttribute("sessions", sessionRepository.findAll());
        model.addAttribute("tutors", tutorRepository.findAll());
        model.addAttribute("students", studentRepository.findAll());
        model.addAttribute("subjects", Subject.values());
        model.addAttribute("bookForm", new BookForm());
        return "sessions";
    }

    @PostMapping("/sessions")
    public String bookSession(
            @ModelAttribute BookForm bookForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            bookingService.bookSession(new BookingService.BookSessionRequest(
                    bookForm.tutorId,
                    bookForm.studentId,
                    bookForm.subject,
                    bookForm.startsAt,
                    bookForm.durationMinutes,
                    bookForm.notes
            ));
            redirectAttributes.addFlashAttribute("success", "Session booked.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/sessions";
    }

    @PostMapping("/sessions/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancelSession(id);
            redirectAttributes.addFlashAttribute("success", "Session cancelled.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/sessions";
    }

    private Set<Subject> parseSubjects(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .map(s -> s.replace(' ', '_'))
                .map(value -> {
                    try {
                        return Subject.valueOf(value);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(s -> s != null)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static class BookForm {
        private Long tutorId;
        private Long studentId;
        private Subject subject;
        private LocalDateTime startsAt;
        private int durationMinutes;
        private String notes;

        public Long getTutorId() {
            return tutorId;
        }

        public void setTutorId(Long tutorId) {
            this.tutorId = tutorId;
        }

        public Long getStudentId() {
            return studentId;
        }

        public void setStudentId(Long studentId) {
            this.studentId = studentId;
        }

        public Subject getSubject() {
            return subject;
        }

        public void setSubject(Subject subject) {
            this.subject = subject;
        }

        public LocalDateTime getStartsAt() {
            return startsAt;
        }

        public void setStartsAt(LocalDateTime startsAt) {
            this.startsAt = startsAt;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(int durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
