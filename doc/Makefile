
all: Aufbau_Katalogangebot.docx

# the file numbered-sections.docx contains defaults for heading layout
# and document language
Aufbau_Katalogangebot.docx: Aufbau_Katalogangebot.org
	pandoc --toc --reference-doc=numbered-sections.docx -o $@ $<

