package com.prawit.deadstore;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeadStoreDetector {

  // Represents a variable assignment (declaration or reassignment) with its line number.
  static class Assignment {
    String name;
    int line;
    boolean read;

    Assignment(String name, int line) {
      this.name = name;
      this.line = line;
      this.read = false;
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.out.println("Usage: DeadStoreDetector <path-to-java-file>");
      System.exit(1);
    }

    File file = new File(args[0]);
    if (!file.exists()) {
      System.out.println("Error: File not found: " + args[0]);
      System.exit(1);
    }

    CompilationUnit cu = StaticJavaParser.parse(file);
    List<String> deadStores = new ArrayList<>();

    // Visit each method in the file
    cu.findAll(MethodDeclaration.class)
        .forEach(
            method -> {
              // Map from variable name to its current unread assignment
              Map<String, Assignment> unreadAssignments = new HashMap<>();

              // Walk through all statements in order
              method
                  .getBody()
                  .ifPresent(
                      body -> {
                        body.getStatements()
                            .forEach(
                                stmt -> {
                                  if (stmt instanceof ExpressionStmt exprStmt) {
                                    var expr = exprStmt.getExpression();

                                    // Case 1: Variable declaration with initializer (e.g. int x = 10)
                                    if (expr instanceof VariableDeclarationExpr declExpr) {
                                      declExpr
                                          .getVariables()
                                          .forEach(
                                              v -> {
                                                if (v.getInitializer().isPresent()) {
                                                  // Check if initializer reads any variables
                                                  v.getInitializer()
                                                      .get()
                                                      .findAll(NameExpr.class)
                                                      .forEach(
                                                          nameExpr -> {
                                                            String usedName =
                                                                nameExpr.getNameAsString();
                                                            if (unreadAssignments.containsKey(
                                                                usedName)) {
                                                              unreadAssignments.get(usedName).read =
                                                                  true;
                                                            }
                                                          });
                                                  // Record this declaration as unread
                                                  String varName = v.getNameAsString();
                                                  int line =
                                                      v.getBegin().map(p -> p.line).orElse(-1);
                                                  unreadAssignments.put(
                                                      varName, new Assignment(varName, line));
                                                }
                                              });

                                      // Case 2: Assignment expression (e.g. x = 20)
                                    } else if (expr instanceof AssignExpr assignExpr) {
                                      String varName = assignExpr.getTarget().toString();

                                      // Check if right-hand side reads any variables
                                      assignExpr
                                          .getValue()
                                          .findAll(NameExpr.class)
                                          .forEach(
                                              nameExpr -> {
                                                String usedName = nameExpr.getNameAsString();
                                                if (unreadAssignments.containsKey(usedName)) {
                                                  unreadAssignments.get(usedName).read = true;
                                                }
                                              });

                                      // If this variable had an unread assignment before → dead
                                      // store
                                      if (unreadAssignments.containsKey(varName)
                                          && !unreadAssignments.get(varName).read) {
                                        Assignment prev = unreadAssignments.get(varName);
                                        deadStores.add(
                                            "  Variable: " + prev.name + ", Line: " + prev.line);
                                      }

                                      // Record this new assignment as unread
                                      int line = assignExpr.getBegin().map(p -> p.line).orElse(-1);
                                      unreadAssignments.put(varName, new Assignment(varName, line));

                                    } else {
                                      // Case 3: Other expressions (method calls like System.out.println(x))
                                      // Check if any variables are read
                                      expr.findAll(NameExpr.class)
                                          .forEach(
                                              nameExpr -> {
                                                String usedName = nameExpr.getNameAsString();
                                                if (unreadAssignments.containsKey(usedName)) {
                                                  unreadAssignments.get(usedName).read = true;
                                                }
                                              });
                                    }
                                  }
                                });

                        // After all statements — any remaining unread assignments are dead stores
                        unreadAssignments.forEach(
                            (name, assignment) -> {
                              if (!assignment.read) {
                                deadStores.add(
                                    "  Variable: "
                                        + assignment.name
                                        + ", Line: "
                                        + assignment.line);
                              }
                            });
                      });
            });

    // Print results
    if (deadStores.isEmpty()) {
      System.out.println("No dead stores detected.");
    } else {
      System.out.println("Dead store detected:");
      deadStores.forEach(System.out::println);
    }
  }
}
