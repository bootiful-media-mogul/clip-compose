import UIKit
import UniformTypeIdentifiers
import Shared

// the share sheet's "Clip to Mogul": the same clip screen as the app, filled in with what was
// shared (a page's title and url, from safari, say), behind a cancel button
class ShareViewController: UINavigationController {

    override func viewDidLoad() {
        super.viewDidLoad()
        Task { @MainActor in
            let (subject, text) = await self.shared()
            let clip = MainViewControllerKt.ShareViewController(subject: subject, text: text) { [weak self] in
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
            clip.navigationItem.title = "Clip to Mogul"
            clip.navigationItem.leftBarButtonItem = UIBarButtonItem(systemItem: .cancel, primaryAction: UIAction { [weak self] _ in
                self?.extensionContext?.cancelRequest(withError: NSError(domain: NSCocoaErrorDomain, code: NSUserCancelledError))
            })
            self.setViewControllers([clip], animated: false)
        }
    }

    // apps hand over a title (as the item's text) and a url, or just text with a url in it
    private func shared() async -> (String?, String?) {
        let items = self.extensionContext?.inputItems as? [NSExtensionItem] ?? []
        let subject = items.lazy.compactMap { $0.attributedTitle?.string ?? $0.attributedContentText?.string }.first
        let providers = items.flatMap { $0.attachments ?? [] }
        if let provider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.url.identifier) }),
           let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
            return (subject, url.absoluteString)
        }
        if let provider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) }),
           let text = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String {
            return (subject, text)
        }
        return (subject, nil)
    }
}
