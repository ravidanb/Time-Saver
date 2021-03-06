package com.example.amit.timesaver;

import android.support.annotation.NonNull;

import java.io.Serializable;

/**
 * Created by amit on 13/10/17.
 */

public class Task implements Comparable<Task>, Serializable {

    private static final long serialVersionUID = 1L;

    private Course relatedCourse;
    private MyDate dueDate;
    private String description;
    private boolean done;

    public Task() {
    }

    public Task(Course relatedCourse, MyDate dueDate, String description) {
        this.relatedCourse = relatedCourse;
        this.dueDate = dueDate;
        this.description = description;
    }

    public Course getRelatedCourse() {
        return relatedCourse;
    }

    public MyDate getDueDate() {
        return dueDate;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDone() {
        return done;
    }

    public void markAsDone() {
        done = true;
    }

    public void markAsUndone() {
        done = false;
    }

    @Override
    public int compareTo(@NonNull Task task) {
        return dueDate.compareTo(task.dueDate);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Task) {
            Task temp = (Task)obj;
            return relatedCourse.equals(temp.relatedCourse) && dueDate.equals(temp.dueDate) && description.equals(temp.description);
        }
        return false;
    }
}
