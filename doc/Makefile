
all: Aufbau_Katalogangebot.docx

Aufbau_Katalogangebot.docx: Aufbau_Katalogangebot.org
	pandoc --toc --reference-doc=numbered-sections.docx -o $@ $<

