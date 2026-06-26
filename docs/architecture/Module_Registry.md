# ARIS Module Registry

This document defines the ownership contract for every module.

Every feature belongs to exactly one module.

Never violate ownership.

Never duplicate responsibilities.

---

# Brain

Owner

Thinking

Responsibilities

• Intent Analysis

• Context Building

• Planning

• Reasoning

• Decision Making

• Risk Analysis

• Tool Selection

Input

Context

Memory

Intent

Output

Decision

Forbidden

Execution

Accessibility

Database

UI

TTS

---

# Memory

Owner

Knowledge

Responsibilities

Working Memory

Long-term Memory

Semantic Memory

Episodic Memory

Skill Memory

Input

Memory Requests

Output

Relevant Memory

Forbidden

Reasoning

Planning

Execution

---

# Perception

Owner

Environment Understanding

Responsibilities

Accessibility Tree

OCR

Current Screen

Notifications

Clipboard

Current App

Device State

Input

Android Framework

Output

Structured Context

Forbidden

Decision Making

Execution

---

# Conversation

Owner

Communication

Responsibilities

Speech

TTS

Dialogue

Clarification

Conversation Context

Input

User

Output

Conversation Events

Forbidden

Accessibility

Planning

Automation

---

# Actions

Owner

Execution

Responsibilities

Click

Swipe

Scroll

Type

Back

Home

Launch Apps

Media Controls

Input

Decision

Output

Execution Result

Forbidden

Thinking

Planning

Memory

---

# Learning

Owner

Improvement

Responsibilities

Reflection

Experience

Optimization

Pattern Detection

Workflow Learning

Input

Execution History

Output

Learned Knowledge

Forbidden

Execution

UI

Accessibility

---

# Overlay

Owner

Visual Feedback

Responsibilities

Floating UI

Animations

Progress

Status

Input

Events

Output

UI Feedback

Forbidden

Business Logic

Reasoning

Execution

---

# Audio

Owner

Voice

Responsibilities

Wake Word

Speech Recognition

Noise Processing

Microphone

Input

Audio

Output

Text

Forbidden

Planning

Decision Making

---

# Tools

Owner

External Capabilities

Responsibilities

Phone

SMS

Browser

Maps

Camera

Calendar

Files

Bluetooth

WiFi

Contacts

Weather

Calculator

Each tool owns only itself.

Never combine tools.

---

# Core

Owner

Infrastructure

Responsibilities

Interfaces

Base Models

Logger

Configuration

Result Types

Shared Contracts

Forbidden

Business Logic

---

# Utilities

Owner

Reusable Helpers

Responsibilities

Formatting

Extensions

Parsing

Validation

Conversion

Forbidden

Application State

Business Logic

---

# Cross Module Rules

Brain requests.

Memory answers.

Perception observes.

Conversation communicates.

Actions execute.

Learning improves.

Overlay displays.

Tools provide capabilities.

Core connects everything.

Utilities help everyone.

---

# Architecture Contract

One Module

↓

One Responsibility

↓

One Owner

↓

One Contract

This rule must never be violated.