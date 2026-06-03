### What were the most challenging parts of this phase and how did you overcome them?

The most challenging part for me was starting from total zero knowledge of both program analysis
and tools like WALA. Before this phase, I had never worked with static analysis tools, so many concepts
like call graphs, class hierarchies, pointer analysis and analysis scope were completely new to me in general.

The difficulty came from two directions concurrently. On the conceptual side, I had to
understand what WALA was actually doing before I could know why things were failing. 
On the tooling side, I ran into setup problems: the Spotless formatter failing the build, 
`python` not being recognized on macOS (needed `python3`), and a NullPointerException from a missing exclusions file
that took many attempts to resolve.

To overcome this, I broke everything down into small pieces and refused to move
forward or skip to the real program until I understood each part.
I tried to explain each concept of WALA back to myself in simple terms. For example,
re-explain everything I learned on this part like I am five years old. That's how I can overcome from zero to something.