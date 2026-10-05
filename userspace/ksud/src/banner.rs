use figlet_rs::FIGlet;

pub fn print_banner() -> String {
    let logo_printer = FIGlet::small().unwrap();

    logo_printer.convert("BakaSU").unwrap().to_string()
}
