# Reactive-Streams Viz

## Usage:

Invoke:

    sbt run

Open browser to the following: http://127.0.0.1:8080/index.html

For more interactive development, you may prefer to run `sbt`, and then run `~ reStart`, which will automatically rebuild the project and restart the server when the Scala sources change.

# Samples:

A different sample can be used by modifying the value `demoableFlow`, as seen in `src/main/scala/m/main.scala`.

- **Numbers**: Demonstrates backpressure when one branch of a grouping is the bottleneck.
- **Shipping**: More complex example; overview below.

## Flow example overviews

### Overnight Shipping packages:

Overview:

- For each package...
- Has it already been labeled for Hazardous?
  - Yes: let it go on
  - No:
    - Does it weigh more than 40 lbs?
        Yes: Manually search it (slow!)
        No:  Automated scan
- Has it already been labeled fragile?
  - Yes: let it go on
  - No:
    - Does a quick glance reveal something Fragile?
      - Yes: label it
      - No: Search it
- Groups: Hazardous, hazardous+fragile, fragile, standard
  - 4 different shippers for package classes:
    - Standard: 80 packages at a time
    - Hazardous+fragile: 10
    - Fragile: 20 items at a time
    - Hazardous: 40
