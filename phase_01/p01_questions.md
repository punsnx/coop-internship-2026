# What were the most challenging parts of this phase and how did you overcome them?

Hello, I'm Sirisuk. For this phase, this is my very first time using the WALA framework. I tried a lot to understand how it works.
Fun fact that's I never do the program Analysis It'd be so hard to know what program are doing. The most challaging part is

1. I start to read the compiler concepts book to understand the real world compilation process in low level 
2. Switching back to the Wala manual is very hard because the framework document is too rare for finding in the internet 
   - but I found the helpfull resource from the ibm the tutorial class, and some slides. it helps me to understand the framework quickly 
3. I tried wrtting Java code for testing the WALA but it stuck to limited of my knowledge to the Wala environment variable setup
   - I solve this by finding the internet, reading the communities resources and tried peek the code to WALA source code and read it manually. 
   - For this case I have some fundamental knowlege for Unix and shell programming and luckily my mysetup environment I already have is well organized so this help me much to edit the variable and code some scripts to make the program possible to run
4. There are some tricky, that's running Wala have different method of loading the code according to the IR construction I stuck with how do I run the ScopeDirCallGraph because it use different apprach to load the code with Eclipse Java Compiler
   - Since I read the code I noticed that there is some method like classLoaderFactory I guess that like the Factory Pattern and see the that class has similar loader that ECJClassLoader
   - I deeply read into the related different of that classloader and found the different across the project that there are 2 loading mehtod and this ECJ only use in this class while other not and make me understand much more how the WALA load the class 
   - It's tricky that It is also related to the compiler parser and construct the IR technique but the WALA maily load byte code that is the phase of frontend process to continue only casting with class hierachy and the ECJ does tricky with in memory compiler frontend and with ECJclassloader it convert into the form of IR that can be use with class Hierachy.
5. Fun part I studied the Java standard library that being use with the Wala framework that is the Java rt.jar that the very the heart of Wala that is Primordial
   - It's indicated that if you try to analyze anything e.g. program call Java.Util collection but the program don't know how it work, this library help program know java base class library, the super class, interface and all standard lib implementations
   - I stuck with this so much because cause there is many way to define the analysis scope what scope lib to use
     - The ScopeFile, By Coding and The Wala Properties.
   - I found the nessessary config properties that let the program know where to find the lib be cause in some example does need this properties because it has no define with in the program code
   - But that still not solve the problem that why it cannot run so I tried to bring the Java rt.jar of my current version jdk I use to the program
   - And I was suprised that there are nothing related to rt file in side the JAVA_HOME, so I decided that there may be someting wrong so I continue search and found out that the rt.jar is deprecated and for newer version it's jmods file
   - That's new approach is for helping devide the library into modular for reduce loading memory. the standard library with load only if program want to use it. 
   - That's great but the how do I fix it cause the program still need rt.jar and the jmods not meet the same type. I found that when Wala loading is behave like the Collection so It's load all .jar with in the stdlib folder and jmods can convert into .jar file
   - So I started making the shell script to make the java.base.jmods covert into the java.bass.jar that WALA can use, and that solve the problem.
6. Writing the the automation script is fun but quiet challenging to make it able to reproduce the result with different environment for varities of OS and different version of jdk (e.g. my friend pc).
   - For easily testing I use the scripts to automate many thing and tried optimize the variable and relative path to make the script more dynamic and reproducible. 
7. During the week, Planning is also important to make sure that work will done and every will have their own works
   - I also do the Project Manager role and help my friends a lot from doing 
     - github repository setup, formatting, testing scripts, and much more
   - By creating task list and Assigning task using Agile approach help our team so much to work together and make sure that the work will done.

**Thanks for reading**
   
   - Sirisuk Tharntham
