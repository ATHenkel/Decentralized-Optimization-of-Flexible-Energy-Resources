# Centralized Optimization Model

This repository contains a centralized optimization model for managing the operation of alkaline water electrolysis. The model is built using IBM ILOG CPLEX Optimization Studio and reads input data from Excel files. The model aims to minimize operational costs while meeting production demands and adhering to operational constraints.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Setup](#setup)
- [Running the Model](#running-the-model)
- [Input Data Format](#input-data-format)
- [Output](#output)
- [Contributing](#contributing)
- [License](#license)

## Prerequisites

- Java Development Kit (JDK) 8 or later
- IBM ILOG CPLEX Optimization Studio
- Apache POI Library for reading/writing Excel files

## Project Structure
CentralizedOptimization/
├── CentralizedOptimization/
│ ├── OptimizationModel.java
│ └── ...
├── in/
│ ├── InputData.xlsx
├── out/
│ └── OptimizationResults.xlsx
└── README.md

## Setup

1. **Clone the repository:**

   ```bash
   git clone https://github.com/yourusername/CentralizedOptimization.git
   cd CentralizedOptimization

## Running the model
1. **Compile the Java code:
 ```bash
  javac -cp "path/to/cplex.jar:path/to/poi.jar:path/to/poi-ooxml.jar" CentralizedOptimization/CentralizedOptimization/OptimizationModel.java

2. Run the model:
```bash
  java -cp "path/to/cplex.jar:path/to/poi.jar:path/to/poi-ooxml.jar:." CentralizedOptimization.CentralizedOptimization.OptimizationModel




