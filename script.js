document.addEventListener('DOMContentLoaded', function () {
    const galleryItems = document.querySelectorAll('.gallery-item');
  
    galleryItems.forEach(item => {
      item.addEventListener('click', () => {
        const imgSrc = item.querySelector('img').src;
        const lightbox = document.createElement('div');
        lightbox.classList.add('lightbox');
        lightbox.innerHTML = `
          <div class="lightbox-content">
            <img src="${imgSrc}" alt="Gallery image">
            <span class="close">&times;</span>
          </div>
        `;
  
        document.body.appendChild(lightbox);
        const handleClose = (e) => {
          if (!e.target.matches('.lightbox-content img')) {
            lightbox.remove();
            document.removeEventListener('click', handleClose);
          }
        };
        setTimeout(() => {
          document.addEventListener('click', handleClose);
        }, 50);
      });
    });
  });