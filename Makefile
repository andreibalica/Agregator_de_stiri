JCC = javac
JVM = java
MAIN = Tema1
SRC = *.java
CP = .:lib/*

.PHONY: build run clean

build:
	$(JCC) -cp "$(CP)" $(SRC)

run:
	$(JVM) -cp "$(CP)" $(MAIN) $(ARGS)

clean:
	rm -f *.class