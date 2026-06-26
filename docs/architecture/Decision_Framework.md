# ARIS Decision Framework

## Purpose

This document defines how ARIS makes decisions.

Every implementation involving reasoning must follow this framework.

Never bypass this process.

---

# Golden Rule

Never act immediately.

Always think before acting.

Observation

↓

Understanding

↓

Planning

↓

Validation

↓

Decision

↓

Execution

↓

Verification

↓

Learning

---

# Step 1 — Observation

Collect all available information.

User request

Current application

Current screen

Visible UI

Notifications

Clipboard

Battery

Network

Time

Permissions

Device State

Never make decisions without sufficient context.

---

# Step 2 — Understanding

Determine

Primary Intent

Secondary Intent

Target

Parameters

Constraints

Confidence

If confidence is too low,

request clarification.

Never guess.

---

# Step 3 — Context Evaluation

Ask internally

What is happening?

Where is the user?

Which app is open?

Is the requested action possible?

What information is still missing?

---

# Step 4 — Memory Retrieval

Retrieve only relevant memory.

Working Memory

Long-term Memory

Skill Memory

Semantic Memory

Never retrieve unnecessary memories.

---

# Step 5 — Planning

Break the task into logical steps.

Never execute directly.

Every complex task requires a plan.

Plans must be reversible whenever possible.

---

# Step 6 — Tool Selection

Choose the minimum required tool.

Prefer local Android APIs.

Use Accessibility only when required.

Use LLM only when reasoning is necessary.

Never use expensive tools for simple tasks.

---

# Step 7 — Risk Analysis

Classify every task.

LOW

MEDIUM

HIGH

CRITICAL

Examples

Open Calculator

LOW

Delete Photos

HIGH

Factory Reset

CRITICAL

Money Transfer

CRITICAL

High-risk operations require confirmation.

Critical operations always require confirmation.

---

# Step 8 — Decision

Return a structured Decision object.

Decision contains

Goal

Plan

Tool

Risk

Confidence

Clarification

Expected Result

Reason

Never return executable actions.

---

# Step 9 — Execution Verification

After execution verify

Was the action successful?

Did the UI change?

Did an error occur?

Should retry happen?

Never assume success.

---

# Step 10 — Reflection

After completion ask

What happened?

Was the task successful?

Can future performance improve?

Should a workflow be remembered?

Store only useful learning.

---

# LLM Decision Rules

Use local intelligence first.

Only use LLM for

Complex reasoning

Planning

Conversation

Summarization

Translation

Creative tasks

Ambiguous requests

Never use LLM for

Opening apps

Simple settings

Known workflows

Repeated actions

Device toggles

Simple navigation

---

# Clarification Rules

Ask the user only when absolutely necessary.

Missing recipient

Missing file

Ambiguous contact

Multiple possible actions

Unsafe request

Otherwise continue autonomously.

---

# Failure Rules

Never crash.

Never stop silently.

Retry when safe.

Replan when possible.

Explain failures clearly.

---

# Learning Rules

Learn only from successful verified workflows.

Never learn failed behaviour.

Never overwrite reliable workflows without evidence.

---

# Final Principle

ARIS should behave like an intelligent engineer.

Not like a script.

Every action should be explainable.

Every decision should be reproducible.

Every improvement should make future decisions better.