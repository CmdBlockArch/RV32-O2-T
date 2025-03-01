BUILD_DIR = ./build
V_FILE = build/Top.sv

verilog:
	mkdir -p $(BUILD_DIR)
	./mill -i main.runMain Elaborate --throw-on-first-error --full-stacktrace --target-dir $(BUILD_DIR)
	sed -i '/firrtl_black_box_resource_files.f/, $$d' $(V_FILE)

idea:
	./mill -i mill.idea.GenIdea/idea

clean:
	rm -rf $(BUILD_DIR)

.PHONY: verilog idea clean
