package com.unios.service.academics;

import com.unios.dto.CourseTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CourseTemplateService {

    private final Map<String, List<CourseTemplate>> templates = new HashMap<>();

    public CourseTemplateService() {
        initTemplates();
    }

    private void initTemplates() {
        // B.E/B.Tech Computer Science
        templates.put("B.E/B.Tech Computer Science", Arrays.asList(
            new CourseTemplate("Introduction to Programming", 4, Collections.emptyList(), true),
            new CourseTemplate("Data Structures & Algorithms", 4, Arrays.asList("Introduction to Programming"), true),
            new CourseTemplate("Database Management Systems", 3, Collections.emptyList(), true),
            new CourseTemplate("Operating Systems", 3, Arrays.asList("Data Structures & Algorithms"), true),
            new CourseTemplate("Discrete Mathematics", 3, Collections.emptyList(), true),
            new CourseTemplate("Computer Networks", 3, Collections.emptyList(), true),
            new CourseTemplate("Software Engineering", 3, Collections.emptyList(), true),
            new CourseTemplate("Object Oriented Programming", 3, Arrays.asList("Introduction to Programming"), true)
        ));

        // B.E/B.Tech Artificial Intelligence
        templates.put("B.E/B.Tech Artificial Intelligence", Arrays.asList(
            new CourseTemplate("Python for AI", 4, Collections.emptyList(), true),
            new CourseTemplate("Machine Learning Foundations", 4, Arrays.asList("Python for AI"), true),
            new CourseTemplate("Neural Networks & Deep Learning", 4, Arrays.asList("Machine Learning Foundations"), true),
            new CourseTemplate("Artificial Intelligence Ethics", 2, Collections.emptyList(), true),
            new CourseTemplate("Probability & Statistics", 3, Collections.emptyList(), true),
            new CourseTemplate("Natural Language Processing", 3, Arrays.asList("Machine Learning Foundations"), true),
            new CourseTemplate("Computer Vision", 3, Arrays.asList("Machine Learning Foundations"), true),
            new CourseTemplate("Data Science with R", 3, Collections.emptyList(), false)
        ));

        // B.E/B.Tech Electronics & Communication
        templates.put("B.E/B.Tech Electronics & Communication", Arrays.asList(
            new CourseTemplate("Electronic Devices & Circuits", 4, Collections.emptyList(), true),
            new CourseTemplate("Digital System Design", 4, Collections.emptyList(), true),
            new CourseTemplate("Signals & Systems", 3, Collections.emptyList(), true),
            new CourseTemplate("Microprocessors & Microcontrollers", 4, Arrays.asList("Digital System Design"), true),
            new CourseTemplate("Analog Communication", 3, Arrays.asList("Signals & Systems"), true),
            new CourseTemplate("Digital Communication", 3, Arrays.asList("Signals & Systems"), true),
            new CourseTemplate("VLSI Design", 3, Collections.emptyList(), true),
            new CourseTemplate("Control Systems", 3, Collections.emptyList(), true)
        ));

        // B.E/B.Tech Information Technology
        templates.put("B.E/B.Tech Information Technology", Arrays.asList(
            new CourseTemplate("Web Technologies", 4, Collections.emptyList(), true),
            new CourseTemplate("Cloud Computing", 3, Collections.emptyList(), true),
            new CourseTemplate("Cyber Security", 3, Collections.emptyList(), true),
            new CourseTemplate("Human Computer Interaction", 3, Collections.emptyList(), true),
            new CourseTemplate("IT Project Management", 3, Collections.emptyList(), true),
            new CourseTemplate("System Administration", 3, Collections.emptyList(), true),
            new CourseTemplate("E-Commerce Foundations", 3, Collections.emptyList(), false),
            new CourseTemplate("Mobile App Development", 3, Collections.emptyList(), true)
        ));
    }

    public List<CourseTemplate> getTemplatesForProgram(String programName) {
        return templates.getOrDefault(programName, Collections.emptyList());
    }
}
