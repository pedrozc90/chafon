# Contare Chafon Module

## Description

Aplicação prototipo do leitor Chafon.

## Device

- `IP`: `192.168.1.200`
- `Port`: `2022`

## Usage

```bash
# build JAR
./mvnw clean compile package -DskipTests
```

```bash
# run mockup
java -jar target/contare-chafon-module-1.0.0.jar --config application.yml
```

## Configuration

```yaml
device:
  MACAddress: "2C-AC-44-04-97-01"
  ip: "192.168.1.200"
  port: 2022

  antennas:
    # Maximum number of physical antenna ports on the device
    num: 4
    # Map which antenna ports are enabled (keys are port numbers: 1..num)
    map:
      1: true
      2: false
      3: true
      4: false

  # When true, the application prints device commands and extra logs
  verbose: false

  # Frequency accepted formats:
  # 1) channel band specification: use `band`, and channel range `minN` / `maxN` (integers)
  # 2) frequency range specification: use `min` / `max` expressed in MHz (floats or ints)
  frequency:
    band: 21
    minN: 0
    maxN: 34
```

## Frequency Band

| Name         | Band | Frequency Function                               | Works |
|:-------------|:----:|:-------------------------------------------------|:-----:|
| Chinese 2    |  1   | Fs = 920.125 + N * 0.25 (MHz) where N ∈ [0, 19]  |  Yes  |
| US           |  2   | Fs = 902.75  + N * 0.5  (MHz) where N ∈ [0, 49]  |  Yes  |
| Korean       |  3   | Fs = 917.1   + N * 0.2  (MHz) where N ∈ [0, 31]  |  Yes  |
| EU           |  4   | Fs = 865.1   + N * 0.2  (MHz) where N ∈ [0, 14]  |  Yes  |
| Chinese 1    |  8   | Fs = 840.125 + N * 0.25 (MHz) where N ∈ [0, 19]  |  Yes  |
| US 3         |  12  | Fs = 902.0   + N * 0.5  (MHz) where N ∈ [0, 52]  |  Yes  |
| Hong Kong    |  16  | Fs = 920.25  + N * 0.5  (MHz) where N ∈ [0, 9]   |  Yes  |
| Taiwan       |  17  | Fs = 920.75  + N * 0.5  (MHz) where N ∈ [0, 13]  |  Yes  |
| ETSI UPPER   |  18  | Fs = 916.30  + N * 1.2  (MHz) where N ∈ [0, 2]   |  Yes  |
| Malaysia     |  19  | Fs = 919.25  + N * 0.5  (MHz) where N ∈ [0, 7]   |  Yes  |
| Brazil       |  21  | Fs = 902.75  + N * 0.5  (MHz) where N ∈ [0, 9]   |  No   |
| Brazil       |  21  | Fs = 910.25  + N * 0.5  (MHz) where N ∈ [10, 34] |  No   |
| Thailand     |  22  | Fs = 920.25  + N * 0.5  (MHz) where N ∈ [0, 9]   |  No   |
| Singapore    |  23  | Fs = 920.25  + N * 0.5  (MHz) where N ∈ [0, 9]   |  Yes  |
| Australia    |  24  | Fs = 920.25  + N * 0.5  (MHz) where N ∈ [0, 9]   |  Yes  |
| India        |  25  | Fs = 865.10  + N * 0.6  (MHz) where N ∈ [0, 3]   |  Yes  |
| Uruguay      |  26  | Fs = 916.25  + N * 0.5  (MHz) where N ∈ [0, 22]  |  No   |
| Vietnam      |  27  | Fs = 918.75  + N * 0.5  (MHz) where N ∈ [0, 7]   |  No   |
| Israel       |  28  | Fs = 916.25  + N * 0.5  (MHz) where N ∈ [0, 0]   |  Yes  |
| Indonesia    |  29  | Fs = 917.25  + N * 0.5  (MHz) where N ∈ [0, 0]   |  No   |
| Indonesia    |  29  | Fs = 919.75  + N * 0.5  (MHz) where N ∈ [1, 3]   |  No   |
| New Zealand  |  30  | Fs = 922.25  + N * 0.5  (MHz) where N ∈ [0, 9]   |  No   |
| Japan 2      |  31  | Fs = 916.80  + N * 1.2  (MHz) where N ∈ [0, 3]   |  No   |
| Peru         |  32  | Fs = 916.25  + N * 0.5  (MHz) where N ∈ [0, 22]  |  Yes  |
| Russia       |  33  | Fs = 916.20  + N * 1.2  (MHz) where N ∈ [0, 3]   |  Yes  |
| South Africa |  34  | Fs = 915.60  + N * 0.2  (MHz) where N ∈ [0, 16]  |  Yes  |
| Philippines  |  35  | Fs = 918.25  + N * 0.5  (MHz) where N ∈ [0, 3]   |  Yes  |
| Ukraine      |  -   | Fs = 868.0   + N * 0.1  (MHz) where N ∈ [0, 6]   |  No   |
| Peru         |  -   | Fs = 916.2   + N * 0.9  (MHz) where N ∈ [0, 11]  |  No   |
| EU 3         |  -   | Fs = 865.7   + N * 0.6  (MHz) where N ∈ [0, 3]   |  No   |

> **ETSI** = European Telecommunications Standards Institute
