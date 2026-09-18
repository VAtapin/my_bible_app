import SharedLogic
import SwiftUI

@MainActor
final class BibleBrowserModel: ObservableObject {
    @Published var books: [BibleBook] = []
    @Published var chapter: BibleChapter?
    @Published var isLoadingBooks = false
    @Published var isLoadingChapter = false
    @Published var booksFailed = false
    @Published var chapterFailed = false

    private let loader = BibleBrowserLoader()
    private var booksRequestID = UUID()
    private var chapterRequestID = UUID()

    func loadBooks(translationCode: String) {
        let requestID = UUID()
        booksRequestID = requestID
        isLoadingBooks = true
        booksFailed = false
        books = []

        loader.loadBooks(
            translationCode: translationCode,
            onSuccess: { [weak self] books in
                guard let self, self.booksRequestID == requestID else { return }
                self.books = books.sorted { $0.order < $1.order }
                self.isLoadingBooks = false
            },
            onError: { [weak self] _ in
                guard let self, self.booksRequestID == requestID else { return }
                self.booksFailed = true
                self.isLoadingBooks = false
            }
        )
    }

    func loadChapter(translationCode: String, bookSlug: String, number: Int) {
        let requestID = UUID()
        chapterRequestID = requestID
        isLoadingChapter = true
        chapterFailed = false
        chapter = nil

        loader.loadChapter(
            translationCode: translationCode,
            bookSlug: bookSlug,
            chapterNumber: Int32(number),
            onSuccess: { [weak self] chapter in
                guard let self, self.chapterRequestID == requestID else { return }
                self.chapter = chapter
                self.isLoadingChapter = false
            },
            onError: { [weak self] _ in
                guard let self, self.chapterRequestID == requestID else { return }
                self.chapterFailed = true
                self.isLoadingChapter = false
            }
        )
    }

    deinit {
        loader.close()
    }
}

struct BibleBrowserView: View {
    let language: String
    let translations: [TranslationSummary]
    let onBack: () -> Void

    @StateObject private var model = BibleBrowserModel()
    @State private var translationCode = ""
    @State private var selectedBook: BibleBook?
    @State private var selectedChapter: Int?

    var body: some View {
        Group {
            if let book = selectedBook, let chapter = selectedChapter {
                ChapterReaderView(
                    language: language,
                    translationCode: translationCode,
                    book: book,
                    chapterNumber: chapter,
                    model: model,
                    onBack: { selectedChapter = nil },
                    onChapterChange: { selectedChapter = $0 }
                )
            } else if let book = selectedBook {
                ChapterPickerView(
                    language: language,
                    book: book,
                    onBack: { selectedBook = nil },
                    onSelect: { selectedChapter = $0 }
                )
            } else {
                BooksView(
                    language: language,
                    translations: translations,
                    selectedTranslationCode: translationCode,
                    model: model,
                    onBack: onBack,
                    onTranslationChange: selectTranslation,
                    onBookSelect: { selectedBook = $0 }
                )
            }
        }
        .task {
            guard translationCode.isEmpty else { return }
            selectTranslation(translations.first?.code ?? "")
        }
    }

    private func selectTranslation(_ code: String) {
        guard !code.isEmpty else { return }
        translationCode = code
        selectedBook = nil
        selectedChapter = nil
        model.loadBooks(translationCode: code)
    }
}

private struct BooksView: View {
    let language: String
    let translations: [TranslationSummary]
    let selectedTranslationCode: String
    @ObservedObject var model: BibleBrowserModel
    let onBack: () -> Void
    let onTranslationChange: (String) -> Void
    let onBookSelect: (BibleBook) -> Void

    private var oldTestament: [BibleBook] {
        model.books.filter { $0.canonicalBook?.testament == "old" }
    }

    private var newTestament: [BibleBook] {
        model.books.filter { $0.canonicalBook?.testament == "new" }
    }

    private var otherBooks: [BibleBook] {
        model.books.filter {
            $0.canonicalBook?.testament != "old" && $0.canonicalBook?.testament != "new"
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ReaderHeader(title: localized("bible.books", language: language), onBack: onBack)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    Text(localized("bible.translation", language: language))
                        .font(.headline)
                        .foregroundStyle(AppPalette.ink)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(translations, id: \.code) { translation in
                                Button(action: { onTranslationChange(translation.code) }) {
                                    Text(translation.shortName ?? translation.language.code.uppercased())
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(translation.code == selectedTranslationCode ? Color.white : AppPalette.ink)
                                        .padding(.horizontal, 16)
                                        .frame(minHeight: 42)
                                        .background(
                                            translation.code == selectedTranslationCode ? AppPalette.navy : Color.white,
                                            in: Capsule()
                                        )
                                        .overlay { Capsule().stroke(AppPalette.border, lineWidth: 1) }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.vertical, 10)

                    if model.isLoadingBooks {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 80)
                    } else if model.booksFailed {
                        ErrorView(
                            language: language,
                            message: localized("bible.books.error", language: language),
                            retry: { model.loadBooks(translationCode: selectedTranslationCode) }
                        )
                    } else {
                        BookGroup(title: localized("bible.old", language: language), books: oldTestament, onSelect: onBookSelect)
                        BookGroup(title: localized("bible.new", language: language), books: newTestament, onSelect: onBookSelect)
                        if !otherBooks.isEmpty {
                            BookGroup(title: localized("bible.books", language: language), books: otherBooks, onSelect: onBookSelect)
                        }
                    }
                }
                .padding(20)
            }
        }
        .background(AppPalette.cream.ignoresSafeArea())
    }
}

private struct BookGroup: View {
    let title: String
    let books: [BibleBook]
    let onSelect: (BibleBook) -> Void

    var body: some View {
        if !books.isEmpty {
            Text(title)
                .font(.system(.title2, design: .serif).weight(.bold))
                .foregroundStyle(AppPalette.navy)
                .padding(.top, 16)
                .padding(.bottom, 6)

            ForEach(books, id: \.slug) { book in
                Button(action: { onSelect(book) }) {
                    HStack(spacing: 12) {
                        Text(String(book.order))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(AppPalette.navy)
                            .frame(width: 36, height: 36)
                            .background(AppPalette.lightBlue, in: Circle())
                        Text(book.name)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(AppPalette.ink)
                        Spacer()
                        Text(String(book.chaptersCount))
                            .font(.caption)
                            .foregroundStyle(AppPalette.blue)
                        Image(systemName: "chevron.right")
                            .foregroundStyle(AppPalette.blue)
                    }
                    .padding(.vertical, 12)
                }
                .buttonStyle(.plain)
                Divider().foregroundStyle(AppPalette.border)
            }
        }
    }
}

private struct ChapterPickerView: View {
    let language: String
    let book: BibleBook
    let onBack: () -> Void
    let onSelect: (Int) -> Void

    private let columns = [GridItem(.adaptive(minimum: 58), spacing: 10)]

    var body: some View {
        VStack(spacing: 0) {
            ReaderHeader(title: book.name, onBack: onBack)
            Text(localized("bible.chapters", language: language))
                .font(.system(.title2, design: .serif).weight(.bold))
                .foregroundStyle(AppPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)

            ScrollView {
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(1...max(Int(book.chaptersCount), 1), id: \.self) { chapter in
                        Button(action: { onSelect(chapter) }) {
                            Text(String(chapter))
                                .fontWeight(.bold)
                                .foregroundStyle(AppPalette.navy)
                                .frame(width: 58, height: 58)
                                .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
                                .overlay { RoundedRectangle(cornerRadius: 16).stroke(AppPalette.border) }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)
            }
        }
        .background(AppPalette.cream.ignoresSafeArea())
    }
}

private struct ChapterReaderView: View {
    let language: String
    let translationCode: String
    let book: BibleBook
    let chapterNumber: Int
    @ObservedObject var model: BibleBrowserModel
    let onBack: () -> Void
    let onChapterChange: (Int) -> Void

    @AppStorage("bookmarks") private var bookmarksCSV = ""
    @AppStorage("readerFontSize") private var fontSize = 19.0

    private var bookmarks: Set<String> {
        Set(bookmarksCSV.split(separator: "|").map(String.init))
    }

    private var title: String {
        "\(book.name) · \(localized("bible.chapter", language: language, chapterNumber))"
    }

    var body: some View {
        VStack(spacing: 0) {
            ReaderHeader(title: title, onBack: onBack)

            Group {
                if model.isLoadingChapter {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if model.chapterFailed {
                    ErrorView(
                        language: language,
                        message: localized("bible.chapter.error", language: language),
                        retry: loadChapter
                    )
                } else if let chapter = model.chapter {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(chapter.verses, id: \.osisRef) { verse in
                                VerseView(
                                    language: language,
                                    chapter: chapter,
                                    verse: verse,
                                    fontSize: fontSize,
                                    bookmarked: bookmarks.contains(verse.osisRef),
                                    onBookmark: { toggleBookmark(verse.osisRef) }
                                )
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            HStack {
                Button(action: { onChapterChange(max(chapterNumber - 1, 1)) }) {
                    Image(systemName: "chevron.left").frame(width: 44, height: 44)
                }
                .disabled(chapterNumber <= 1)

                Button("A−") { fontSize = max(fontSize - 1, 15) }
                    .frame(maxWidth: .infinity)

                Text("\(chapterNumber) / \(book.chaptersCount)")
                    .fontWeight(.bold)
                    .foregroundStyle(AppPalette.navy)

                Button("A+") { fontSize = min(fontSize + 1, 28) }
                    .frame(maxWidth: .infinity)

                Button(action: { onChapterChange(min(chapterNumber + 1, Int(book.chaptersCount))) }) {
                    Image(systemName: "chevron.right").frame(width: 44, height: 44)
                }
                .disabled(chapterNumber >= Int(book.chaptersCount))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Color.white.shadow(color: .black.opacity(0.12), radius: 5, y: -2))
        }
        .background(AppPalette.cream.ignoresSafeArea())
        .task(id: chapterNumber) { loadChapter() }
    }

    private func loadChapter() {
        model.loadChapter(
            translationCode: translationCode,
            bookSlug: book.slug,
            number: chapterNumber
        )
    }

    private func toggleBookmark(_ reference: String) {
        var values = bookmarks
        if !values.insert(reference).inserted {
            values.remove(reference)
        }
        bookmarksCSV = values.sorted().joined(separator: "|")
    }
}

private struct VerseView: View {
    let language: String
    let chapter: BibleChapter
    let verse: BibleVerse
    let fontSize: Double
    let bookmarked: Bool
    let onBookmark: () -> Void

    private var shareText: String {
        "\(verse.plainText)\n\n\(chapter.book.name) \(chapter.chapter.number):\(verse.number) · \(chapter.translation.shortName ?? chapter.translation.name)"
    }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text(String(verse.number))
                .font(.caption.weight(.bold))
                .foregroundStyle(AppPalette.blue)
                .padding(.top, 4)

            Text(verse.plainText)
                .font(.system(size: fontSize, design: .serif))
                .foregroundStyle(AppPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)

            VStack(spacing: 4) {
                Button(action: onBookmark) {
                    Image(systemName: bookmarked ? "bookmark.fill" : "bookmark")
                        .frame(width: 40, height: 40)
                }
                .accessibilityLabel(localized(bookmarked ? "bible.bookmark.remove" : "bible.bookmark.add", language: language))

                ShareLink(item: shareText) {
                    Image(systemName: "square.and.arrow.up")
                        .frame(width: 40, height: 40)
                }
                .accessibilityLabel(localized("bible.share", language: language))
            }
            .foregroundStyle(AppPalette.blue)
        }
        .padding(.vertical, 8)
        Divider().foregroundStyle(AppPalette.border)
    }
}

private struct ReaderHeader: View {
    let title: String
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .frame(width: 44, height: 44)
            }
            Text(title)
                .font(.system(.title3, design: .serif).weight(.bold))
                .foregroundStyle(AppPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(Color.white)
    }
}

private struct ErrorView: View {
    let language: String
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text(message)
                .foregroundStyle(AppPalette.ink)
                .multilineTextAlignment(.center)
            Button(localized("action.retry", language: language), action: retry)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, minHeight: 220)
        .padding()
    }
}
