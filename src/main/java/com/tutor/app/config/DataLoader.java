package com.tutor.app.config;

import com.tutor.app.model.Student;
import com.tutor.app.model.Subject;
import com.tutor.app.model.Tutor;
import com.tutor.app.repository.StudentRepository;
import com.tutor.app.repository.TutorRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class DataLoader {

    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;

    public DataLoader(TutorRepository tutorRepository, StudentRepository studentRepository) {
        this.tutorRepository = tutorRepository;
        this.studentRepository = studentRepository;
    }

    @PostConstruct
    public void seed() {
        if (tutorRepository.count() == 0) {
            Tutor a = new Tutor();
            a.setName("Avery Johnson");
            a.setEmail("avery@tutor.app");
            a.setBio("STEM tutor focused on test prep.");
            a.setHourlyRate(new BigDecimal("55.00"));
            a.setSubjects(Set.of(Subject.MATH, Subject.SCIENCE, Subject.COMPUTER_SCIENCE));
            tutorRepository.save(a);

            Tutor b = new Tutor();
            b.setName("Mia Torres");
            b.setEmail("mia@tutor.app");
            b.setBio("Writing and history tutor for middle/high school.");
            b.setHourlyRate(new BigDecimal("45.00"));
            b.setSubjects(Set.of(Subject.ENGLISH, Subject.HISTORY));
            tutorRepository.save(b);
        }

        if (studentRepository.count() == 0) {
            Student s1 = new Student();
            s1.setName("Jordan Lee");
            s1.setEmail("jordan@student.app");
            s1.setGoal("Improve algebra grade to A.");
            studentRepository.save(s1);

            Student s2 = new Student();
            s2.setName("Riley Chen");
            s2.setEmail("riley@student.app");
            s2.setGoal("Prepare for AP U.S. History exam.");
            studentRepository.save(s2);
        }
    }
}
