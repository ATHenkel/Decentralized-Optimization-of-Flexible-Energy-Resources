# **Methodology for Distributed Optimization of Flexible Energy Resources**  
### **Semi-Automated Model Transformation and Deployment**  

This repository provides a **reference implementation** of a methodology for **transforming centralized optimization models** into **distributed ADMM-based optimization models** using a **multi-agent system**. The implementation is designed to **automate the decomposition** of centralized models and enable **scalable distributed optimization** across multiple computing units.

---

## **Repository Structure**  

```bash
root/
│-- in/  
│   ├── model.ampl  # Centralized optimization model  
│   ├── config.xlsx  # (Optional) Excel file for agent system parameterization  
│  
│-- out/  
│   ├── update_1.ampl  # ADMM update files  
│   ├── update_2.ampl  
│  
│-- amplTransformator/  
│   ├── src/main/java/com/project/amplTransformator/  
│       ├── CentralizedToADMMConverter.java  # Converts centralized model to ADMM updates  
│  
│-- agents/  
│   ├── src/main/java/com/project/agents/  
│       ├── ADMMAgent.java  # Optimization agent  
│       ├── AMSAgent.java  # Agent Management System (coordination)  
│  
│-- models/  
│   ├── src/main/java/com/project/models/  
│       ├── Electrolyzer.java  # Electrolyzer representation  
│       ├── Period.java  # Time period representation
│       ├── ...
│  
│-- behaviour/  
│   ├── src/main/java/com/project/behaviour/  
│       ├── LoadParametersBehaviour.java  # JADE behavior for distributed optimization
│       ├── ...  
│  
│-- config/  
│   ├── application.properties  # Configuration file for system settings  
│  
│-- lib/  
│   ├── jade.jar  # JADE library for multi-agent communication  
│  
│-- README.md  # This file  
│-- pom.xml  # Maven dependencies (if using Maven)  
│-- build.gradle  # Gradle dependencies (if using Gradle)  
│-- LICENSE  # License file  


---
## **Setup & Installation**  

### **Prerequisites**  
Before running the system, ensure that you have installed:  
- **Java 11+** (or a compatible version)  
- **Maven** or **Gradle** (depending on your build system)  
- **JADE** (for the multi-agent system, included in `/lib/`)  

### **Installation**  
1. Clone the repository:  
   ```bash
   git clone https://github.com/your-repo-url.git  
   cd your-repo

## **Contact**  

For any questions or inquiries, feel free to contact:  

📌 **Vincent Henkel**  
📧 [Vincent.Henkel@hsu-hh.de](mailto:Vincent.Henkel@hsu-hh.de)
