# Wootube
* 전체적으로 비디오 기능에 대한 설명

## Video Domain

* common field
    * ``Long id`` - 아이디(primary key)
    * ``Long createTime`` - 만들어진 시간
    * ``Long updateTime`` - 수정한 시간
* video field
    * ``String title`` - 비디오 제목
    * ``String description`` - 비디오 설명
    * ``String contentPath`` - S3에서 저장된 비디오 url
    * ``String thumbnailPath`` - S3에서 저장된 썸네일 url
    * ``String originFileName`` - 비디오 파일 이름
    * ``String thumbnailFileName`` - 비디오 썸네일 이름
    * ``Long views`` - 시청수
    * ``User writer`` - 비디오 주인
        

## Video Repository

* methods
    * ``findAllRandom`` : 비디오 파일을 메인화면이나 비디오 리스트 화면에서 랜덤으로 보여주는 리스트
    * ``findByWriter`` : 비디오 주인을 찾기 위한 기능

## Video Service

* Create
    ```java
    public VideoResponseDto create(MultipartFile uploadFile, VideoRequestDto videoRequestDto, Long writerId) {
        S3UploadFileFactory s3UploadFileFactory = new S3UploadFileFactory(uploadFile, fileConverter, fileUploader).invoke();
        S3UploadFileFactory s3UploadFileFactory = new S3UploadFileFactory(uploadFile, fileConverter, fileUploader).invoke();

        User writer = userService.findByIdAndIsActiveTrue(writerId);
        Video video = modelMapper.map(videoRequestDto, Video.class);

        video.initialize(s3UploadFileFactory.getVideoUrl(), s3UploadFileFactory.getThumbnailUrl(),
                s3UploadFileFactory.getOriginFileName(), s3UploadFileFactory.getThumbnailFileName(), writer);
        return modelMapper.map(videoRepository.save(video), VideoResponseDto.class);
    }
    ```
   * S3UploadFileFactory에서 비디오와 썸네일을 만들어 S3에 저장시키고 url을 가지고 온다.
   * video initialize를 통해 비디오 정보를 저장한다.
   
* Read
    ```java
    public VideoResponseDto findVideo(Long id) {
        Video video = findById(id);
        increaseViews(video);
        return modelMapper.map(video, VideoResponseDto.class);
    }
    ```
    * 비디오를 불러오는 경우 해당 비디오의 시청수가 올라간다.
    
* Update
    ```java
    public void update(Long id, MultipartFile uploadFile, VideoRequestDto videoRequestDto, Long writerId) {
        Video video = findById(id);
        matchWriter(writerId, id);

        if (!uploadFile.isEmpty()) {
            fileUploader.deleteFile(video.getOriginFileName(), UploadType.VIDEO);
            fileUploader.deleteFile(video.getThumbnailFileName(), UploadType.THUMBNAIL);

            S3UploadFileFactory s3UploadFileFactory = new S3UploadFileFactory(uploadFile, fileConverter, fileUploader).invoke();

            video.updateVideo(s3UploadFileFactory.getVideoUrl(), s3UploadFileFactory.getOriginFileName(),
                    s3UploadFileFactory.getThumbnailUrl(), s3UploadFileFactory.getThumbnailFileName());
        }

        video.updateTitle(videoRequestDto.getTitle());
        video.updateDescription(videoRequestDto.getDescription());
    }
    ```
    * 비디오 자체를 수정하는 경우 S3에 있는 파일을 삭제 시킨후 새로 만드는 로직으로 구현했다.
    * 비디오 외 부가적인 부분만 수정하는 경우 일반 Update와 같이 구현

* Delete
    ```java
    public void deleteById(Long videoId, Long userId) {
        Video video = findById(videoId);
        if (!video.matchWriter(userId)) {
            throw new UserAndWriterMisMatchException();
        }
        fileUploader.deleteFile(video.getOriginFileName(), UploadType.VIDEO);
        fileUploader.deleteFile(video.getThumbnailFileName(), UploadType.THUMBNAIL);
        videoRepository.deleteById(video.getId());
    }
    ```
    * S3에 있는 비디오를 먼저 삭제 후 video 삭제
    
## S3 Uploader

* S3UploadFileFactory.class
    ```java
    public S3UploadFileFactory invoke() {
        File convertedVideo = fileConverter.convert(uploadFile);
        File convertedThumbnail = fileConverter.convert(convertedVideo);

        videoUrl = fileUploader.uploadFile(convertedVideo, UploadType.VIDEO);
        thumbnailUrl = fileUploader.uploadFile(convertedThumbnail, UploadType.THUMBNAIL);

        originFileName = convertedVideo.getName();
        thumbnailFileName = convertedThumbnail.getName();

        convertedVideo.delete();
        convertedThumbnail.delete();
        return this;
    }
    ```
    * MultiPartFile로 들어온 데이터를 비디오와 썸네일 파일로 추출해서 로컬에 저장시킨다.
    * convert시킨 비디오와 썸네일 파일을 upload시켜 s3에 저장시킨다.
    * 저장완료 되면 로컬에 남아있는 비디오와 썸네일 파일을 삭제 시킨다.
        
 * FileConverter
    * MultipartFile로 들어오는 경우(비디오)
        ```java
        public File convert(MultipartFile file) {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS);
            File convertFile = new File(LocalDateTime.now().format(dateTimeFormatter) + file.getOriginalFilename());

            try {
               if (convertFile.createNewFile()) {
                   FileOutputStream fos = new FileOutputStream(convertFile);
                   fos.write(file.getBytes());
                   return convertFile;
               }
           } catch (IOException e) {
               convertFile.delete();
           }
           throw new FileConvertException();
        }
        ```
        * 파일 이름을 시분초를 붙여 file을 생성시키고 로컬에 저장한다.
    
    * File로 들어오는 경우(썸네일)
        ```java
        public File convert(File videoFile) {
            String fileName = videoFile.getAbsolutePath();
            String baseName = fileName.substring(fileName.lastIndexOf("\\") + 1, fileName.lastIndexOf("."));

            double frameNumber = getFrameNumber(videoFile);
            return getThumbNailImageFile(fileName, baseName, frameNumber);
        }
        
        private double getFrameNumber(File videoFile) {
            try {
               SeekableByteChannel byteChannel = NIOUtils.readableFileChannel(videoFile);
                MP4Demuxer demuxer = new MP4Demuxer(byteChannel);
                SeekableDemuxerTrack track = demuxer.getVideoTrack();
               return track.getMeta().getTotalDuration() / 5.0;
            } catch (IOException e) {
                videoFile.delete();
                throw new InvalidFileExtensionException();
            }
        }

        private File getThumbNailImageFile(String fileName, String baseName, double frameNumber) {
            try {
                Picture thumbnail = FrameGrab.getNativeFrame(new File(fileName), frameNumber);
                BufferedImage img = AWTUtil.toBufferedImage(thumbnail);

                File thumbnailFile = new File(baseName + ".png");
                if (!thumbnailFile.exists()) {
                    thumbnailFile.createNewFile();
                }

                BufferedImage thumbImg = Scalr.resize(img, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, THUMBNAIL_WIDTH,       Scalr.OP_ANTIALIAS);
                ImageIO.write(thumbImg, "png", thumbnailFile);
                return thumbnailFile;
            } catch (IOException | JCodecException e) {
                throw new FileUploadException();
            }
        }
        ```                
        * 동기화 처리를 위해 Seekable을 사용한다.
        * 비디오 파일을 바이트 채널로 변경한다.
        * 썸네일을 추출하기 위해 전체 동영상 비디오 트랙을 5초를 나눠 나머지를 썸네일로 추출하는 방법이다.
        * jcodec에 있는 FramGrab을 사용해 아까 만든 초와 비디오파일을 주어 그 사진을 추출한다.
        * 스칼라 resize를 통해 이미지 성능을 축소화 했다.
        * 추출하고 나면 BufferImage로 변경한다.
        
## Test Code
* VideoServiceTest
   ```java
   @Test
    @DisplayName("서비스에서 비디오를 저장한다.")
    void create() throws IOException {
        createMockVideo();

        verify(modelMapper, atLeast(1)).map(testVideoRequestDto, Video.class);
        verify(videoRepository, atLeast(1)).save(testVideo);
    }

    private void createMockVideo() throws IOException {
        File convertedVideo = mock(File.class);
        File convertedThumbnail = mock(File.class);

        mockUploadVideo(convertedVideo, convertedThumbnail);

        given(modelMapper.map(testVideoRequestDto, Video.class)).willReturn(testVideo);
        given(userService.findByIdAndIsActiveTrue(USER_ID)).willReturn(writer);

        videoService.create(testUploadMultipartFile, testVideoRequestDto, USER_ID);
    }
    ```
    * Mock을 사용 -> 사용하는 이유? - [참고](http://egloos.zum.com/sakula99/v/2912503)
    * given , when, then을 사용하여 테스트 검증
    
* 인수 테스트
    ```java
    @Test
    @DisplayName("비디오를 저장한다.")
    void save() throws IOException {
        File file = ResourceUtils.getFile("classpath:test_file.mp4");
        byte[] fileContent = Files.readAllBytes(file.toPath());

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("uploadFile", new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return "test_file.mp4";
            }
        }, MediaType.parseMediaType("video/mp4"));
        bodyBuilder.part("title", "video_title");
        bodyBuilder.part("description", "video_description");
        bodyBuilder.part("writerId", 1);

        requestWithBodyBuilder(bodyBuilder, POST, "/videos/new")
                .expectStatus().isFound();
        stopS3Mock();
    }
    ```
    * 하나의 공통 컨트롤러 테스트를 만들어 AcceptanceTest와 비슷하게 인수테스트를 구현
    * S3Mock을 사용해 S3에 간섭이 없도록 테스트를 구현 -> 테스트만 돌릴 수 있다.
    * test를 위한 S3Mock Config Class를 만들어야 함
      * config에는 8001번 포트로 S3Mock서버를 돌리며 프로덕션 설정에 있는 S3와 겹치지 않도록 primary로 설정
        
## 아쉬웠던 점

* 썸네일 추출 할 때 한개가 아닌 다양한 개수를 뽑아서 보여주었으면 좋을 것 같다.
* 현재 확장자 명으로만 비디오 검증을 하고 있는데 이 방법은 보안에 취약하다 -> 악성파일이 확장자만 변경시켜 보낼 경우 거를 수 없음
   * 따라서 파일 시그니처를 통해 검증을 하면 좋을 것 같다고 생각한다.
* 비디오 업로드 할 때 크기가 큰 파일일 경우 시간이 오래 걸린다는 단점이 있다.
   * 비동기 처리로 알람을 띄우게 하면 좋을 것 같다.
* 크기가 큰 비디오를 부를 경우 시간이 오래 걸린다.
   * 비디오 세그먼트로 나눠서 요청해 보면 어떨까? -> 대부분 미디어 서비스는 이와 같이 하는 것 같음
   * 캐시로 저장시켜도 나쁘지 않다고 생각한다. -> 찾아봐야 될 것 같다. 
* 테스트에서 data.sql을 따로 만들지 않고 테스트를 할 수 있지 않았을까??
   * 더 다양한 시도를 해봐야겠다.
