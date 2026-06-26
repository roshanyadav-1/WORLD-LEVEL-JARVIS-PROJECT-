# ARIS Brain Architecture

## Purpose

The Brain is the central reasoning system of ARIS.

Its responsibility is NOT to execute actions.

Its responsibility is to think.

It receives information from multiple modules.

It reasons.

It plans.

It decides.

It returns a Decision.

Execution is handled by the Action module.

---

# Brain Philosophy

Brain ≠ LLM

The LLM is only one reasoning component.

The Brain owns the complete thinking process.

Every decision must be explainable.

Every decision must be reproducible.

Every decision must be verifiable.

---

# Brain Responsibilities

✔ Understand user intent

✔ Understand current context

✔ Understand device state

✔ Build world model

✔ Retrieve memory

✔ Create execution plan

✔ Choose tools

✔ Evaluate risk

✔ Decide whether clarification is required

✔ Produce structured decision

---

# Brain Never Does

❌ Accessibility

❌ UI

❌ Click

❌ Swipe

❌ Type

❌ TTS

❌ Overlay

❌ Database

❌ Android Views

❌ Gesture execution

---

# Brain Pipeline

Voice/Text

↓

Intent Analysis

↓

Context Building

↓

Memory Retrieval

↓

World Model Update

↓

Task Planning

↓

Reasoning Engine

↓

Risk Validation

↓

Tool Selection

↓

Decision Generation

↓

Return Decision

---

# Internal Modules

Brain/

Intent/

Context/

Memory/

Planning/

Reasoning/

Validation/

Decision/

Models/

Registry/

---

# Brain Modules

Intent Analyzer

Understands user goal.

---

Context Builder

Collects all relevant information.

---

World Model

Represents current device state.

---

Memory Manager

Provides relevant memories.

---

Task Planner

Creates logical execution steps.

---

Reasoning Engine

Uses LLM when necessary.

---

Risk Validator

Prevents unsafe decisions.

---

Tool Selector

Chooses best execution tool.

---

Decision Manager

Produces final Decision object.

---

# Decision Object

Every Brain request returns only one object.

Decision

contains

Goal

Plan

Risk

Confidence

Tool

Clarification

Reasoning

Expected Result

No executable code.

No Android actions.

---

# LLM Usage Rules

LLM is expensive.

LLM is slow.

LLM should be treated as a specialist.

Use LLM only when:

Complex reasoning

Natural conversation

Ambiguous commands

Planning

Summarization

Translation

Explanation

Creative tasks

Do NOT use LLM for:

Open app

Back

Home

Bluetooth On

Volume Up

Brightness

Simple Settings

Known workflows

Common automation

Always prefer local intelligence.

---

# Brain Memory Rules

Brain never stores memory directly.

Brain requests memory.

Memory module responds.

Brain continues reasoning.

---

# Brain Tool Rules

Brain never executes tools.

Brain only selects tools.

Execution happens elsewhere.

---

# Brain Safety Rules

Every decision receives:

Confidence Score

Risk Score

Explanation

Clarification Requirement

If confidence is low,

clarify.

If risk is high,

ask confirmation.

---

# Future Expansion

Brain must support:

Multiple LLMs

Offline reasoning

Cloud reasoning

Plugins

Robotics

Desktop

IoT

Without redesign.

Architecture must remain stable.