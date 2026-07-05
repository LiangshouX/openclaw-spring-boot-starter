---
name: weather
description: Check weather forecasts and conditions for any location
user-invocable: true
command-dispatch: tool
command-tool: weather_lookup
command-arg-mode: raw
homepage: https://example.com/weather-skill
metadata: {"openclaw": {"requires": {"env": ["WEATHER_API_KEY"]}, "primaryEnv": "WEATHER_API_KEY"}}
---

# Weather Skill

When the user asks about weather, temperature, or forecasts for any location,
use the `weather_lookup` tool to fetch current conditions.

## Usage Guidelines

- Always specify the location clearly in your response
- Include temperature in both Celsius and Fahrenheit
- Mention humidity and wind speed when relevant
- For forecasts, show the next 3 days by default

## Example Interactions

User: "What's the weather like in Beijing?"
→ Call `weather_lookup` with `{"location": "Beijing"}` and present the results.
