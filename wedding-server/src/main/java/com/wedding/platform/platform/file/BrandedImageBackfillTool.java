package com.wedding.platform.platform.file;

public final class BrandedImageBackfillTool {

    private BrandedImageBackfillTool() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: BrandedImageBackfillTool <storage-root> [--overwrite]");
        }
        boolean overwrite = args.length == 2 && "--overwrite".equals(args[1]);
        if (args.length == 2 && !overwrite) {
            throw new IllegalArgumentException("The only supported option is --overwrite");
        }

        BrandedImagePathResolver pathResolver = new BrandedImagePathResolver("originals", "branded");
        CollectionImageStorageService service = new CollectionImageStorageService(
                args[0],
                "originals",
                "previews",
                "thumbnails",
                1920,
                480,
                2560,
                "classpath:/brand/watermark.png",
                pathResolver
        );
        CollectionImageStorageService.BrandedBackfillResult result =
                service.backfillBrandedImages(overwrite);
        System.out.printf(
                "Branded image backfill completed: generated=%d skipped=%d%n",
                result.generated(),
                result.skipped()
        );
    }
}
