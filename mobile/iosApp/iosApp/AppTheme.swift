import Foundation
import SwiftUI

enum AppPalette {
    static let navy = Color(red: 47.0 / 255.0, green: 91.0 / 255.0, blue: 124.0 / 255.0)
    static let blue = Color(red: 91.0 / 255.0, green: 126.0 / 255.0, blue: 166.0 / 255.0)
    static let lightBlue = Color(red: 229.0 / 255.0, green: 236.0 / 255.0, blue: 244.0 / 255.0)
    static let cream = Color(red: 250.0 / 255.0, green: 248.0 / 255.0, blue: 243.0 / 255.0)
    static let border = Color(red: 230.0 / 255.0, green: 224.0 / 255.0, blue: 213.0 / 255.0)
    static let gold = Color(red: 212.0 / 255.0, green: 175.0 / 255.0, blue: 107.0 / 255.0)
    static let ink = Color(red: 23.0 / 255.0, green: 50.0 / 255.0, blue: 74.0 / 255.0)
}

func localized(_ key: String, language: String, _ arguments: CVarArg...) -> String {
    guard
        let path = Bundle.main.path(forResource: language, ofType: "lproj"),
        let bundle = Bundle(path: path)
    else {
        return key
    }

    let format = bundle.localizedString(forKey: key, value: key, table: nil)
    return String(format: format, locale: Locale(identifier: language), arguments: arguments)
}
