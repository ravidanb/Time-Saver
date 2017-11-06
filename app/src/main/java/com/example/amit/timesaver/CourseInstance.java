package com.example.amit.timesaver;

import java.io.Serializable;

/**
 * Created by amit on 13/10/17.
 */

class CourseInstance implements Serializable{

    private static final long serialVersionUID = 1L;


    private Course course;
    private eDay dayOfWeek;
    private int startHour;
    private int endHour;
    private String professorName;

    enum eDay {
        SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY
    }

    public CourseInstance() {
    }

    public CourseInstance(Course course, eDay dayOfWeek, int startHour, int endHour, String professorName) {
        this.course = course;
        this.dayOfWeek = dayOfWeek;
        this.startHour = startHour;
        this.endHour = endHour;
        this.professorName = professorName;
    }

    public CourseInstance(Course course, eDay dayOfWeek, int startHour, int endHour) {
        this(course,dayOfWeek,startHour,endHour, null);
    }

    public Course getCourse() {
        return course;
    }

    public eDay getDayOfWeek() {
        return dayOfWeek;
    }

    public int getStartHour() {
        return startHour;
    }

    public int getEndHour() {
        return endHour;
    }

    public String getProfessorName() {
        return professorName;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof CourseInstance) {
            CourseInstance temp = (CourseInstance) obj;
            return course.equals(temp.getCourse()) && startHour == temp.getStartHour();
        }
        return false;
    }
}
