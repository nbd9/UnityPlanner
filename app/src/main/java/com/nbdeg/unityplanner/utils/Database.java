package com.nbdeg.unityplanner.utils;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.nbdeg.unityplanner.AssignmentWidget;
import com.nbdeg.unityplanner.R;
import com.nbdeg.unityplanner.data.Assignment;
import com.nbdeg.unityplanner.data.ChangedCourseName;
import com.nbdeg.unityplanner.data.Course;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class controlling all assignments and courses synced to the database
 */
public class Database {

    private static FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

    public static DatabaseReference userDb = FirebaseDatabase.getInstance().getReference().child("users").child(user.getUid());
    public static DatabaseReference courseDb = userDb.child("classes");
    private static DatabaseReference assignmentDb = userDb.child("assignments");
    public static DatabaseReference allAssignmentDb = assignmentDb.child("all");
    public static DatabaseReference dueAssignmentDb = assignmentDb.child("due");
    public static DatabaseReference doneAssignmentDb = assignmentDb.child("done");
    private static DatabaseReference changeCourseDb = userDb.child("changedClasses");

    // Saved as <Original name, new name>
    private static HashMap<String, String> changedCourseNames = new HashMap<>();
    private static ArrayList<Assignment> assignments = new ArrayList<>();
    private static ArrayList<Course> courses = new ArrayList<>();

    /* -- Getters -- */
    public static  HashMap<String, String> getChangedCourseNames() {
        return changedCourseNames;
    }
    public static ArrayList<Assignment>    getAssignments() {
        return assignments;
    }
    public static ArrayList<Course>        getCourses() {
        return courses;
    }
    public static FirebaseUser             getUser() {
        return user;
    }

    /**
     * Refreshes DatabaseReferences and updates ArrayLists
     */
    public static void refreshDatabase(Context context) {
        user = FirebaseAuth.getInstance().getCurrentUser();
        userDb = FirebaseDatabase.getInstance().getReference().child("users").child(user.getUid());
        courseDb = userDb.child("classes");
        assignmentDb = userDb.child("assignments");
        allAssignmentDb = assignmentDb.child("all");
        dueAssignmentDb = assignmentDb.child("due");
        doneAssignmentDb = assignmentDb.child("done");
        changeCourseDb = userDb.child("changedClasses");

        assignments.clear();
        courses.clear();
        changedCourseNames.clear();
        allAssignmentDb.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    Assignment assignment = userSnapshot.getValue(Assignment.class);
                    assignments.add(assignment);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        courseDb.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    Course course = userSnapshot.getValue(Course.class);
                    courses.add(course);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        changeCourseDb.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    ChangedCourseName courseNames = userSnapshot.getValue(ChangedCourseName.class);
                    changedCourseNames.put(courseNames.getOldCourseName(), courseNames.getNewCourseName());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        updateWidget(context);
    }

    /**
     * Adds an assignment to the database
     * @param assignment The added assignment
     */
    public static void createAssignment(Assignment assignment, Context context) {
        updateWidget(context);
        if (assignment.getPercentComplete() == 100) {
            String key = doneAssignmentDb.push().getKey();
            assignment.setID(key);
            doneAssignmentDb.child(key).setValue(assignment);
            allAssignmentDb.child(key).setValue(assignment);
        } else {
            String key = dueAssignmentDb.push().getKey();
            assignment.setID(key);
            dueAssignmentDb.child(key).setValue(assignment);
            allAssignmentDb.child(key).setValue(assignment);
        }

        assignments.add(assignment);
    }

    /**
     * Edits an existing assignment in the database
     * @param newAssignment The new, updated assignment
     * @param oldAssignment The old, previous assignment
     */
    public static void editAssignment(final Assignment newAssignment, Assignment oldAssignment, Context context) {
        updateWidget(context);
        if (oldAssignment.getPercentComplete() == 100) {

            // Assignment used to be finished, check and see if it's not anymore.
            if (newAssignment.getPercentComplete() < 100) {

                // Assignment not finished anymore, update that in database.
                dueAssignmentDb.child(oldAssignment.getID()).setValue(newAssignment);
                allAssignmentDb.child(oldAssignment.getID()).setValue(newAssignment);
                doneAssignmentDb.child(oldAssignment.getID()).removeValue();
            } else {

                // Assignment still finished, just update new values in database.
                doneAssignmentDb.child(oldAssignment.getID()).setValue(newAssignment);
                allAssignmentDb.child(oldAssignment.getID()).setValue(newAssignment);
            }
        } else {

            // Assignment didn't used to be finished. Check if it is now.
            if (newAssignment.getPercentComplete() == 100) {

                // Assignment now finished, update that in database.
                dueAssignmentDb.child(oldAssignment.getID()).removeValue();
                allAssignmentDb.child(oldAssignment.getID()).setValue(newAssignment);
                doneAssignmentDb.child(oldAssignment.getID()).setValue(newAssignment);
            } else {
                // Assignment still due, just update new values in database.
                allAssignmentDb.child(oldAssignment.getID()).setValue(newAssignment);
                dueAssignmentDb.child(oldAssignment.getID()).setValue(newAssignment);
            }
        }
    }

    /**
     * Deletes an assignment from the database
     * @param assignments Old assignment to be deleted
     */
    public static void deleteAssignment(final Assignment assignments, Context context) {
        updateWidget(context);
        if (assignments.getPercentComplete() == 100) {
            // Assignment was finished
            doneAssignmentDb.child(assignments.getID()).removeValue();
            allAssignmentDb.child(assignments.getID()).removeValue();
        } else {
            // Assignment was not finished
            dueAssignmentDb.child(assignments.getID()).removeValue();
            allAssignmentDb.child(assignments.getID()).removeValue();
        }
    }

    /**
     * Adds a course to the database
     * @param course The added course
     */
    public static void createCourse(Course course) {
        String key = courseDb.push().getKey();
        course.setID(key);
        courseDb.child(key).setValue(course);
    }

    /**
     * Edits an existing course in the database
     * @param newCourse The new, updated course
     * @param oldCourse The old, previous course
     */
    public static void editCourse(final Course newCourse, final Course oldCourse) {
        // If name changed, add to database
        if (!newCourse.getName().equals(oldCourse.getName())) {
            changeCourseDb.push().setValue(new ChangedCourseName(oldCourse.getName(), newCourse.getName()));
        }

        // Update All Assignments Under That Name
        for (Assignment assignment : assignments) {
            if (assignment.getDueCourse().getID().equals(oldCourse.getID())) {
                assignment.setDueCourse(newCourse);
                if (assignment.getPercentComplete() == 100) {
                    doneAssignmentDb.child(assignment.getID()).setValue(assignment);
                } else {
                    dueAssignmentDb.child(assignment.getID()).setValue(assignment);
                }
                allAssignmentDb.child(assignment.getID()).setValue(assignment);
            }
        }

        // Updates values in database
        courseDb.child(oldCourse.getID()).setValue(newCourse);
    }

    /**
     * Deletes an existing course and all assignments associated with it
     * @param course The existing course
     */
    public static void deleteCourse(final Course course) {
        // Find all assignments under that name and delete them
        for (Assignment assignment : assignments) {
            if (assignment.getDueCourse() != null) {
                if (assignment.getDueCourse().equals(course)) {
                    if (assignment.getPercentComplete() == 100) {
                        doneAssignmentDb.child(assignment.getID()).removeValue();
                    } else {
                        dueAssignmentDb.child(assignment.getID()).removeValue();
                    }
                    allAssignmentDb.child(assignment.getID()).removeValue();
                }
            }
        }
        courseDb.child(course.getID()).removeValue();
    }

    private static void updateWidget(Context context) {
        AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, AssignmentWidget.class)), R.id.widget_list_view);
    }
}
