
DOCX = Aufbau_Katalogangebot.docx

all: $(DOCX)

# the file numbered-sections.docx contains defaults for heading layout
# and document language
Aufbau_Katalogangebot.docx: Aufbau_Katalogangebot.org
	pandoc --toc --number-sections -o $@ $<

.PHONY : clean
clean :
	rm -f $(DOCX)
