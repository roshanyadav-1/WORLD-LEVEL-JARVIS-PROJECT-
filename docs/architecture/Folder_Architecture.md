# ARIS Folder Architecture

This document defines the permanent project structure.

The folder hierarchy must remain stable.

Never reorganize packages unless explicitly instructed.

---

# Root Modules

app/

brain/
memory/
perception/
conversation/
actions/
tools/
learning/
overlay/
audio/
core/
utilities/
data/
domain/
di/

---

# brain/

Purpose:

Thinking.

Reasoning.

Planning.

Decision making.

Allowed:

Intent Analyzer

Context Builder

Task Planner

Decision Manager

Reasoning Engine

Prompt Builder

Validator

World Model

Forbidden:

Accessibility

Database

UI

Networking

Overlay

Android Views

---

# memory/

Purpose:

Persistent and temporary memory.

Allowed:

Working Memory

Long-term Memory

Semantic Memory

Episodic Memory

Skill Memory

Forbidden:

Decision Making

Accessibility

Execution

---

# perception/

Purpose:

Understanding device state.

Allowed:

Screen Analysis

OCR

Accessibility Tree Parsing

Current App Detection

Notification Analysis

Clipboard Analysis

Forbidden:

Planning

Reasoning

Execution

---

# actions/

Purpose:

Execute validated actions.

Allowed:

Click

Swipe

Scroll

Type

Back

Home

Media Controls

System Actions

Forbidden:

Decision Making

Memory

Reasoning

---

# conversation/

Purpose:

Human interaction.

Allowed:

Speech

TTS

Conversation Context

Clarification

Dialogue

Forbidden:

Accessibility

Planning

Execution

---

# learning/

Purpose:

Continuous improvement.

Allowed:

Reflection

Experience Storage

Pattern Learning

Workflow Optimization

Forbidden:

Execution

UI

Accessibility

---

# tools/

Purpose:

External capabilities.

Examples:

Browser

Maps

Phone

SMS

Calendar

Files

Camera

Contacts

Weather

Calculator

Bluetooth

WiFi

Each tool owns only its own implementation.

---

# overlay/

Purpose:

Visual feedback.

Floating UI.

Subtitles.

Animations.

Progress.

Status.

Never place business logic here.

---

# audio/

Purpose:

Voice input.

Wake word.

Speech Recognition.

Noise filtering.

Microphone.

No reasoning.

---

# core/

Purpose:

Shared abstractions.

Interfaces.

Common models.

Error handling.

Result wrappers.

Logger.

Configuration.

No business logic.

---

# utilities/

Purpose:

Pure helper functions.

Formatting.

Extensions.

Parsing.

Conversions.

Never store application state.

---

# data/

Purpose:

Repositories.

Persistence.

Remote.

Local.

Serialization.

---

# domain/

Purpose:

Business models.

Use Cases.

Repository Interfaces.

Pure Kotlin.

Independent from Android.

---

# di/

Purpose:

Dependency Injection.

Nothing else.

---

# MODULE COMMUNICATION

Allowed

Conversation

↓

Brain

↓

Actions

↓

Perception

↓

Tools

↓

Core

Never bypass module boundaries.

Never create circular dependencies.

---

# IMPORTANT

One folder

One responsibility.

One responsibility

One owner.

Architecture is permanent.

Implementation evolves.