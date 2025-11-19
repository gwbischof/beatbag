#!/usr/bin/env python3
"""Generate simple kick drum sounds as WAV files"""

import numpy as np
import wave
import struct

def generate_kick(filename, duration=0.5, sample_rate=44100, base_freq=60, decay=0.3):
    """Generate a kick drum sound with frequency sweep and decay"""

    # Time array
    t = np.linspace(0, duration, int(sample_rate * duration))

    # Frequency sweep from base_freq down to base_freq/4
    freq = base_freq * np.exp(-t / (duration * 0.1))

    # Generate sine wave with frequency sweep
    phase = 2 * np.pi * np.cumsum(freq) / sample_rate
    signal = np.sin(phase)

    # Apply exponential decay envelope
    envelope = np.exp(-t / decay)

    # Apply envelope to signal
    kick = signal * envelope

    # Add click at beginning for punch
    click_duration = 0.005
    click_samples = int(sample_rate * click_duration)
    noise = np.random.normal(0, 0.3, click_samples)
    click_envelope = np.exp(-np.linspace(0, 10, click_samples))
    click = noise * click_envelope
    kick[:click_samples] += click

    # Normalize
    kick = kick / np.max(np.abs(kick))

    # Convert to 16-bit PCM
    kick_int = (kick * 32767).astype(np.int16)

    # Write WAV file
    with wave.open(filename, 'w') as wav:
        wav.setnchannels(1)  # Mono
        wav.setsampwidth(2)  # 16-bit
        wav.setframerate(sample_rate)
        wav.writeframes(kick_int.tobytes())

    print(f"Generated {filename}")

# Create output directory
import os
output_dir = "app/src/main/res/raw"
os.makedirs(output_dir, exist_ok=True)

# Generate different kick drum variations
generate_kick(f"{output_dir}/kick1.wav", base_freq=55, decay=0.4)  # Deep kick
generate_kick(f"{output_dir}/kick2.wav", base_freq=70, decay=0.25) # Punchy kick
generate_kick(f"{output_dir}/kick3.wav", base_freq=85, decay=0.2)  # Tight kick
generate_kick(f"{output_dir}/kick4.wav", base_freq=50, decay=0.5)  # Sub kick

print("\nKick drum sounds generated successfully!")
print(f"Files saved to: {output_dir}/")
