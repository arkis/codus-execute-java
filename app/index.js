const path = require('path');
const tar = require('tar-stream');
const Docker = require('dockerode');


// Initialize Docker
const docker = new Docker();

/** Make sure that the image is built */
function preflight() {
  docker.listImages().then(images => {
    // Check for the presence of an image tagged codus-execute-java:latest
    if (!images.filter(i => i.RepoTags.includes('codus-execute-java:latest')).length) {
      // If none is present, build the image
      // FIXME: waiting on https://github.com/apocas/dockerode/issues/432
      docker.buildImage({
        context: path.join(__dirname, '..'),
        src: ['Dockerfile'],
      }, {
        t: 'codus-execute-java'
      }).then(n => n.pipe(process.stdout));
    }
  });
}


/**
 * The main function implementing the entire functionality of this package.
 * This function performs all of the following:
 *   0. Build the image if it can't be found
 *   1. Create a container from the image
 *   2. Copy problem info into the container
 *   3. Copy the user's solution into the container
 *   4. Start the container. This will automatically compile, run and test the solution as defined
 *      in the Dockerfile from which the image was built.
 *   5. Copy the results out of the container
 *   6. Destroy the container
 * @param {Object} problem - JSON representation of the problem
 * @param {String} solution - the user's Java code
 */
module.exports = async function main(problem, solution) {
  // Build image if not present
  preflight();

  // Create container
  const container = await docker.createContainer({ Image: 'codus-execute-java' });

  // Create tar archive for copying files into container
  const files = tar.pack();
  await new Promise( // Add problem info
    (resolve) => files.entry({ name: 'tests.json' }, JSON.stringify(problem), resolve)
  );
  await new Promise( // Add user's code
    (resolve) => files.entry({ name: 'Solution.java' }, solution, resolve)
  );
  files.finalize();
  // Copy it into the container
  await container.putArchive(files, { path: '/app'});

  // Start container
  await container.start();
  // Wait for execution to finish
  await container.wait();

  // Get the results of the tests.
  // Get tar file of results.json file
  const resultsTar = await container.getArchive({ path: '/app/results.json' });
  // Pull file out of tar archive
  const results = await new Promise((resolve) => {
    const extract = tar.extract();
    extract.on('entry', (header, stream, next) => {
      // Build Buffer from stream chunks
      const chunks = [];
      stream.on('data', chunk => chunks.push(chunk));
      stream.on('end', () => resolve(Buffer.concat(chunks))); // Resolve after first entry because there's only one
    });
    // Send resultsTar to the extractor
    resultsTar.pipe(extract);
  }).then(b => b.toString('UTF-8')).then(JSON.parse);

  // Remove container
  await container.remove();


  return results;
}
